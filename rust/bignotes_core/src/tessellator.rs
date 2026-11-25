use crate::spline::{CatmullRomSpline, StrokePoint};

pub const FLOATS_PER_VERTEX: usize = 22;
pub const VERTICES_PER_SEGMENT: usize = 6;
pub const FLOATS_PER_SEGMENT: usize = VERTICES_PER_SEGMENT * FLOATS_PER_VERTEX;

#[inline(always)]
fn put_vertex(
    out: &mut [f32],
    offset: usize,
    x: f32,
    y: f32,
    s0x: f32,
    s0y: f32,
    s1x: f32,
    s1y: f32,
    r0: f32,
    r1: f32,
    r_nom0: f32,
    r_nom1: f32,
    r: f32,
    g: f32,
    b: f32,
    a: f32,
    tone: f32,
    m0x: f32,
    m0y: f32,
    m1x: f32,
    m1y: f32,
    nib_x: f32,
    nib_y: f32,
    tilt: f32,
) {
    out[offset] = x;
    out[offset + 1] = y;
    out[offset + 2] = s0x;
    out[offset + 3] = s0y;
    out[offset + 4] = s1x;
    out[offset + 5] = s1y;
    out[offset + 6] = r0;
    out[offset + 7] = r1;
    out[offset + 8] = r_nom0;
    out[offset + 9] = r_nom1;
    out[offset + 10] = r;
    out[offset + 11] = g;
    out[offset + 12] = b;
    out[offset + 13] = a;
    out[offset + 14] = tone;
    out[offset + 15] = m0x;
    out[offset + 16] = m0y;
    out[offset + 17] = m1x;
    out[offset + 18] = m1y;
    out[offset + 19] = nib_x;
    out[offset + 20] = nib_y;
    out[offset + 21] = tilt;
}

#[inline]
pub fn get_spline_step(base_width: f32, is_ink: bool, is_highlighter: bool) -> f32 {
    if is_highlighter {
        3.0f32.max(base_width * 0.65)
    } else if is_ink {
        1.5f32.max(base_width * 0.35).min(6.0)
    } else {
        1.6f32.max(base_width * 0.38).min(6.5)
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
        let base_alpha = (0.56 + 0.28 * density) * opacity_multiplier;
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

#[inline]
fn compute_radii_tone(
    pressure: f32,
    base_width: f32,
    graphite_tone_id: f32,
    is_ink: bool,
    is_highlighter: bool,
    quad_margin_ratio: f32,
) -> (f32, f32, f32) {
    if is_highlighter {
        let nominal_r = (base_width * 2.5 * (0.88 + 0.12 * pressure)).max(6.0);
        (nominal_r, nominal_r, 5.5)
    } else if is_ink {
        let nominal_r = get_ink_radius(pressure, base_width);
        let extra_margin = (nominal_r * quad_margin_ratio).max(0.75);
        let quad_r = nominal_r + extra_margin;
        (quad_r, nominal_r, 3.5)
    } else {
        let p = pressure.clamp(0.0, 1.0);
        let nominal_r = (base_width * (0.32 + 0.68 * p) * 0.5).max(0.8);
        (nominal_r, nominal_r, graphite_tone_id)
    }
}

#[inline]
fn compute_point_alpha(
    pressure: f32,
    speed: f32,
    base_alpha: f32,
    is_ink: bool,
    is_highlighter: bool,
) -> f32 {
    if is_highlighter {
        base_alpha
    } else if is_ink {
        (1.14 - 0.24 * speed.clamp(0.0, 1.0)).clamp(0.86, 1.18)
    } else {
        let p = pressure.clamp(0.0, 1.0);
        (base_alpha * (0.20 + 0.80 * p)).clamp(0.06, 0.90)
    }
}

#[allow(clippy::too_many_arguments)]
fn emit_segment_quad(
    out: &mut [f32],
    offset: usize,
    p0: StrokePoint,
    p1: StrokePoint,
    m0x: f32,
    m0y: f32,
    m1x: f32,
    m1y: f32,
    base_width: f32,
    r: f32,
    g: f32,
    b: f32,
    base_alpha: f32,
    graphite_tone_id: f32,
    is_ink: bool,
    is_highlighter: bool,
    quad_margin_ratio: f32,
) {
    let (r0, r_nom0, tone0) = compute_radii_tone(
        p0.pressure, base_width, graphite_tone_id, is_ink, is_highlighter, quad_margin_ratio,
    );
    let (r1, r_nom1, _) = compute_radii_tone(
        p1.pressure, base_width, graphite_tone_id, is_ink, is_highlighter, quad_margin_ratio,
    );
    let avg_alpha = 0.5
        * (compute_point_alpha(p0.pressure, p0.speed, base_alpha, is_ink, is_highlighter)
            + compute_point_alpha(p1.pressure, p1.speed, base_alpha, is_ink, is_highlighter));

    let max_tilt = if is_highlighter {
        0.0
    } else {
        p0.tilt.max(p1.tilt).clamp(0.0, 1.0)
    };
    let (nib_x, nib_y) = if !is_highlighter && (p0.tilt > 0.005 || p1.tilt > 0.005) {
        let vx = p0.tilt * p0.orientation.cos() + p1.tilt * p1.orientation.cos();
        let vy = p0.tilt * p0.orientation.sin() + p1.tilt * p1.orientation.sin();
        let m = vx.hypot(vy);
        if m > 1e-5 {
            (vx / m, vy / m)
        } else {
            (1.0, 0.0)
        }
    } else {
        (1.0, 0.0)
    };

    let aniso_pad = if is_highlighter || is_ink {
        1.0
    } else {
        1.0 + 1.25 * max_tilt
    };
    let quad_pad = r0.max(r1) * aniso_pad + 0.5;

    let dx = p1.x - p0.x;
    let dy = p1.y - p0.y;
    let len = dx.hypot(dy);
    let (ux, uy) = if len > 1e-4 {
        (dx / len, dy / len)
    } else {
        (1.0, 0.0)
    };
    let nx = -uy;
    let ny = ux;

    let c0x = p0.x - ux * quad_pad - nx * quad_pad;
    let c0y = p0.y - uy * quad_pad - ny * quad_pad;
    let c1x = p1.x + ux * quad_pad - nx * quad_pad;
    let c1y = p1.y + uy * quad_pad - ny * quad_pad;
    let c2x = p1.x + ux * quad_pad + nx * quad_pad;
    let c2y = p1.y + uy * quad_pad + ny * quad_pad;
    let c3x = p0.x - ux * quad_pad + nx * quad_pad;
    let c3y = p0.y - uy * quad_pad + ny * quad_pad;

    put_vertex(out, offset, c0x, c0y, p0.x, p0.y, p1.x, p1.y, r0, r1, r_nom0, r_nom1, r, g, b, avg_alpha, tone0, m0x, m0y, m1x, m1y, nib_x, nib_y, max_tilt);
    put_vertex(out, offset + FLOATS_PER_VERTEX, c1x, c1y, p0.x, p0.y, p1.x, p1.y, r0, r1, r_nom0, r_nom1, r, g, b, avg_alpha, tone0, m0x, m0y, m1x, m1y, nib_x, nib_y, max_tilt);
    put_vertex(out, offset + 2 * FLOATS_PER_VERTEX, c2x, c2y, p0.x, p0.y, p1.x, p1.y, r0, r1, r_nom0, r_nom1, r, g, b, avg_alpha, tone0, m0x, m0y, m1x, m1y, nib_x, nib_y, max_tilt);

    put_vertex(out, offset + 3 * FLOATS_PER_VERTEX, c0x, c0y, p0.x, p0.y, p1.x, p1.y, r0, r1, r_nom0, r_nom1, r, g, b, avg_alpha, tone0, m0x, m0y, m1x, m1y, nib_x, nib_y, max_tilt);
    put_vertex(out, offset + 4 * FLOATS_PER_VERTEX, c2x, c2y, p0.x, p0.y, p1.x, p1.y, r0, r1, r_nom0, r_nom1, r, g, b, avg_alpha, tone0, m0x, m0y, m1x, m1y, nib_x, nib_y, max_tilt);
    put_vertex(out, offset + 5 * FLOATS_PER_VERTEX, c3x, c3y, p0.x, p0.y, p1.x, p1.y, r0, r1, r_nom0, r_nom1, r, g, b, avg_alpha, tone0, m0x, m0y, m1x, m1y, nib_x, nib_y, max_tilt);
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

    let smoothed = if points.len() >= 3 {
        let step_dist = get_spline_step(base_width, is_ink, is_highlighter);
        CatmullRomSpline::interpolate(points, step_dist)
    } else {
        points.to_vec()
    };

    let mut cleaned = Vec::with_capacity(smoothed.len());
    cleaned.push(smoothed[0]);
    for pt in smoothed.iter().skip(1) {
        let prev = cleaned[cleaned.len() - 1];
        if (pt.x - prev.x).hypot(pt.y - prev.y) >= 0.15 {
            cleaned.push(*pt);
        }
    }

    let graphite_tone_id = if is_highlighter {
        5.5
    } else if is_ink {
        3.5
    } else {
        get_graphite_density(color_rgb)
    };
    let (r, g, b, base_alpha) = resolve_color(color_rgb, is_ink, is_highlighter);

    if cleaned.len() == 1 {
        let mut out = vec![0.0f32; FLOATS_PER_SEGMENT];
        emit_segment_quad(
            &mut out, 0, cleaned[0], cleaned[0], 0.0, 0.0, 0.0, 0.0,
            base_width, r, g, b, base_alpha, graphite_tone_id, is_ink, is_highlighter, quad_margin_ratio,
        );
        return out;
    }

    let seg_count = cleaned.len() - 1;
    let mut tangents_x = Vec::with_capacity(seg_count);
    let mut tangents_y = Vec::with_capacity(seg_count);
    for i in 0..seg_count {
        let dx = cleaned[i + 1].x - cleaned[i].x;
        let dy = cleaned[i + 1].y - cleaned[i].y;
        let len = dx.hypot(dy).max(1e-4);
        tangents_x.push(dx / len);
        tangents_y.push(dy / len);
    }

    let mut miters_x = vec![0.0f32; cleaned.len()];
    let mut miters_y = vec![0.0f32; cleaned.len()];
    for j in 1..(cleaned.len() - 1) {
        let sx = tangents_x[j - 1] + tangents_x[j];
        let sy = tangents_y[j - 1] + tangents_y[j];
        let m_len = sx.hypot(sy);
        if m_len > 1e-4 {
            miters_x[j] = sx / m_len;
            miters_y[j] = sy / m_len;
        } else {
            miters_x[j] = tangents_x[j];
            miters_y[j] = tangents_y[j];
        }
    }

    let mut out = vec![0.0f32; seg_count * FLOATS_PER_SEGMENT];
    let mut offset = 0;
    for i in 0..seg_count {
        emit_segment_quad(
            &mut out,
            offset,
            cleaned[i],
            cleaned[i + 1],
            miters_x[i],
            miters_y[i],
            miters_x[i + 1],
            miters_y[i + 1],
            base_width,
            r,
            g,
            b,
            base_alpha,
            graphite_tone_id,
            is_ink,
            is_highlighter,
            quad_margin_ratio,
        );
        offset += FLOATS_PER_SEGMENT;
    }

    out
}
