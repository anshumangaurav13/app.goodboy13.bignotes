use crate::dabs::DabGenerator;
use crate::spline::{CatmullRomSpline, StrokePoint};

pub const FLOATS_PER_VERTEX: usize = 10;
pub const VERTICES_PER_DAB: usize = 6;
pub const FLOATS_PER_DAB: usize = VERTICES_PER_DAB * FLOATS_PER_VERTEX;

#[allow(dead_code)]
#[inline(always)]
fn put_vertex(
    out: &mut [f32],
    offset: usize,
    x: f32,
    y: f32,
    nx: f32,
    ny: f32,
    radius: f32,
    r: f32,
    g: f32,
    b: f32,
    a: f32,
    tone: f32,
) {
    out[offset] = x;
    out[offset + 1] = y;
    out[offset + 2] = nx;
    out[offset + 3] = ny;
    out[offset + 4] = radius;
    out[offset + 5] = r;
    out[offset + 6] = g;
    out[offset + 7] = b;
    out[offset + 8] = a;
    out[offset + 9] = tone;
}

#[cfg(target_arch = "aarch64")]
#[inline(always)]
unsafe fn emit_dab_quad_neon(
    out_ptr: *mut f32,
    x: f32,
    y: f32,
    radius: f32,
    r: f32,
    g: f32,
    b: f32,
    alpha: f32,
    tone: f32,
) {
    use std::arch::aarch64::*;

    let attr_rad_rgb = vld1q_f32([radius, r, g, b].as_ptr());
    let attr_alpha_tone = vld1_f32([alpha, tone].as_ptr());

    const NORMALS: [(f32, f32); 6] = [
        (-1.0, -1.0),
        (1.0, -1.0),
        (1.0, 1.0),
        (-1.0, -1.0),
        (1.0, 1.0),
        (-1.0, 1.0),
    ];

    for (i, &(nx, ny)) in NORMALS.iter().enumerate() {
        let v_ptr = out_ptr.add(i * FLOATS_PER_VERTEX);
        let xy_nx_ny = vld1q_f32([x, y, nx, ny].as_ptr());
        vst1q_f32(v_ptr, xy_nx_ny);
        vst1q_f32(v_ptr.add(4), attr_rad_rgb);
        vst1_f32(v_ptr.add(8), attr_alpha_tone);
    }
}

#[inline]
pub fn get_step_distance(base_width: f32, is_ink: bool, is_highlighter: bool) -> f32 {
    if is_highlighter {
        let nominal_radius = 5.0f32.max(base_width * 2.5);
        3.0f32.max(nominal_radius * 0.35)
    } else {
        let min_radius = if is_ink {
            0.5f32.max(base_width * 0.10)
        } else {
            0.6f32.max(base_width * 0.15)
        };
        let step = min_radius * 0.20;
        if is_ink {
            0.12f32.max(step.min(1.2))
        } else {
            0.15f32.max(step.min(1.5))
        }
    }
}

#[inline]
pub fn resolve_color(color_rgb: i64, is_ink: bool, is_highlighter: bool) -> (f32, f32, f32, f32) {
    let a_raw = (((color_rgb >> 24) & 0xFF) as f32) / 255.0;
    let opacity_multiplier = if a_raw > 0.0 { a_raw } else { 1.0 };
    let r = (((color_rgb >> 16) & 0xFF) as f32) / 255.0;
    let g = (((color_rgb >> 8) & 0xFF) as f32) / 255.0;
    let b = ((color_rgb & 0xFF) as f32) / 255.0;

    if is_highlighter || is_ink {
        (r, g, b, 1.0)
    } else {
        let lightness = 0.299 * r + 0.587 * g + 0.114 * b;
        let density = (1.0 - lightness).clamp(0.0, 1.0);
        let base_alpha = (0.24 + 0.15 * density) * opacity_multiplier;
        (r, g, b, base_alpha)
    }
}

#[inline]
pub fn get_graphite_density(color_rgb: i64) -> f32 {
    let r = (((color_rgb >> 16) & 0xFF) as f32) / 255.0;
    let g = (((color_rgb >> 8) & 0xFF) as f32) / 255.0;
    let b = ((color_rgb & 0xFF) as f32) / 255.0;
    let lightness = 0.299 * r + 0.587 * g + 0.114 * b;
    (1.0 - lightness).clamp(0.0, 1.0)
}

#[inline]
pub fn get_ink_radius(pressure: f32, base_width: f32) -> f32 {
    let p = pressure.clamp(0.0, 1.0);
    let pressure_curve = p * p * 0.45 + p * 0.55;
    let radius_factor = 0.30 + 0.70 * pressure_curve;
    let base_radius = base_width * 0.5;
    let min_radius = 0.4f32.max(base_radius * 0.30);
    min_radius.max(base_radius * radius_factor)
}

pub fn generate_dabs(
    points: &[StrokePoint],
    base_width: f32,
    is_ink: bool,
    is_highlighter: bool,
) -> Vec<StrokePoint> {
    if points.is_empty() {
        return Vec::new();
    }
    let step_distance = get_step_distance(base_width, is_ink, is_highlighter);

    let smoothed_points = if points.len() >= 3 {
        let step_dist = if is_highlighter {
            2.0f32.max(base_width * 0.60)
        } else {
            0.8f32.max(base_width * 0.20)
        };
        CatmullRomSpline::interpolate(points, step_dist)
    } else {
        points.to_vec()
    };

    let mut gen = DabGenerator::new(step_distance);
    let mut dabs = Vec::with_capacity(smoothed_points.len() * 4);
    if let Some(first) = smoothed_points.first() {
        gen.start(first.x, first.y, first.pressure, &mut dabs);
    }
    for pt in smoothed_points.iter().skip(1) {
        gen.add_point(pt.x, pt.y, pt.pressure, &mut dabs);
    }
    dabs
}

pub fn tessellate_stroke(
    points: &[StrokePoint],
    base_width: f32,
    color_rgb: i64,
    is_ink: bool,
    is_highlighter: bool,
    quad_margin_ratio: f32,
) -> Vec<f32> {
    if points.is_empty() {
        return Vec::new();
    }
    let dabs = generate_dabs(points, base_width, is_ink, is_highlighter);
    if dabs.is_empty() {
        return Vec::new();
    }

    let total_vertices = dabs.len() * VERTICES_PER_DAB;
    let mut out = vec![0.0f32; total_vertices * FLOATS_PER_VERTEX];

    let tone_id = if is_highlighter {
        5.5
    } else if is_ink {
        3.0
    } else {
        get_graphite_density(color_rgb)
    };

    let (r, g, b, base_alpha) = resolve_color(color_rgb, is_ink, is_highlighter);
    let mut offset = 0;

    for pt in &dabs {
        let (radius, alpha, dab_tone_id) = if is_highlighter {
            let nominal_radius = (base_width * 2.5 * (0.88 + 0.12 * pt.pressure)).max(6.0);
            (nominal_radius, base_alpha, 5.5)
        } else if is_ink {
            let nominal_radius = get_ink_radius(pt.pressure, base_width);
            let extra_margin = (nominal_radius * quad_margin_ratio).max(2.0);
            let quad_radius = nominal_radius + extra_margin;
            let nominal_ratio = (nominal_radius / quad_radius).clamp(0.05, 0.95);
            (quad_radius, 1.0, 3.0 + nominal_ratio)
        } else {
            let p = pt.pressure.clamp(0.0, 1.0);
            let radius = (base_width * (0.32 + 0.68 * p) * 0.5).max(0.8);
            let alpha = (base_alpha * (0.12 + 0.88 * p)).clamp(0.02, 0.50);
            (radius, alpha, tone_id)
        };

        #[cfg(target_arch = "aarch64")]
        unsafe {
            emit_dab_quad_neon(out.as_mut_ptr().add(offset), pt.x, pt.y, radius, r, g, b, alpha, dab_tone_id);
        }

        #[cfg(not(target_arch = "aarch64"))]
        {
            // 6 vertices per dab (2 triangles forming quad)
            put_vertex(&mut out, offset, pt.x, pt.y, -1.0, -1.0, radius, r, g, b, alpha, dab_tone_id);
            put_vertex(&mut out, offset + 10, pt.x, pt.y, 1.0, -1.0, radius, r, g, b, alpha, dab_tone_id);
            put_vertex(&mut out, offset + 20, pt.x, pt.y, 1.0, 1.0, radius, r, g, b, alpha, dab_tone_id);

            put_vertex(&mut out, offset + 30, pt.x, pt.y, -1.0, -1.0, radius, r, g, b, alpha, dab_tone_id);
            put_vertex(&mut out, offset + 40, pt.x, pt.y, 1.0, 1.0, radius, r, g, b, alpha, dab_tone_id);
            put_vertex(&mut out, offset + 50, pt.x, pt.y, -1.0, 1.0, radius, r, g, b, alpha, dab_tone_id);
        }

        offset += FLOATS_PER_DAB;
    }

    out
}
