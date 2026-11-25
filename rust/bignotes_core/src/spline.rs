#[derive(Debug, Clone, Copy, PartialEq)]
pub struct StrokePoint {
    pub x: f32,
    pub y: f32,
    pub pressure: f32,
    pub time_millis: i64,
    pub tilt: f32,
    pub orientation: f32,
    pub speed: f32,
}

impl StrokePoint {
    #[inline(always)]
    pub fn new(x: f32, y: f32, pressure: f32, time_millis: i64) -> Self {
        Self {
            x,
            y,
            pressure,
            time_millis,
            tilt: 0.0,
            orientation: 0.0,
            speed: 0.0,
        }
    }

    #[inline(always)]
    pub fn with_dynamics(
        x: f32,
        y: f32,
        pressure: f32,
        time_millis: i64,
        tilt: f32,
        orientation: f32,
        speed: f32,
    ) -> Self {
        Self {
            x,
            y,
            pressure,
            time_millis,
            tilt,
            orientation,
            speed,
        }
    }
}

#[inline(always)]
fn interpolate_tilt_orientation(a: StrokePoint, b: StrokePoint, t: f32) -> (f32, f32) {
    let tilt = (a.tilt + (b.tilt - a.tilt) * t).clamp(0.0, 1.0);
    if tilt <= 0.005 {
        return (0.0, 0.0);
    }
    let ax = a.tilt * a.orientation.cos();
    let ay = a.tilt * a.orientation.sin();
    let bx = b.tilt * b.orientation.cos();
    let by = b.tilt * b.orientation.sin();
    let vx = ax + (bx - ax) * t;
    let vy = ay + (by - ay) * t;
    let mag = vx.hypot(vy);
    let orient = if mag > 1e-5 { vy.atan2(vx) } else { b.orientation };
    (tilt, orient)
}

pub struct CatmullRomSpline;

impl CatmullRomSpline {
    /// Interpolates a sequence of StrokePoints with a Catmull-Rom spline.
    /// Intermediate points are spaced by `step_distance` for smooth tessellation.
    pub fn interpolate(points: &[StrokePoint], step_distance: f32) -> Vec<StrokePoint> {
        let count = points.len();
        if count < 2 {
            return points.to_vec();
        }
        if count == 2 {
            return Self::interpolate_linear(points[0], points[1], step_distance);
        }

        let mut result = Vec::with_capacity(count * 4);
        result.push(points[0]);

        for i in 0..(count - 1) {
            let p0 = if i > 0 { points[i - 1] } else { points[0] };
            let p1 = points[i];
            let p2 = points[i + 1];
            let p3 = if i + 2 < count { points[i + 2] } else { p2 };

            let dx = p2.x - p1.x;
            let dy = p2.y - p1.y;
            let segment_length = dx.hypot(dy);
            if segment_length < 1e-3 {
                continue;
            }

            let steps = 1.max((segment_length / step_distance) as usize);
            let inv_steps = 1.0 / (steps as f32);

            let mut s = 1;
            #[cfg(target_arch = "aarch64")]
            {
                while s + 3 <= steps {
                    let pts = unsafe { Self::evaluate_4wide_neon(p0, p1, p2, p3, s, inv_steps) };
                    result.extend_from_slice(&pts);
                    s += 4;
                }
            }

            while s <= steps {
                let t = (s as f32) * inv_steps;
                let interpolated = Self::evaluate(p0, p1, p2, p3, t);
                result.push(interpolated);
                s += 1;
            }
        }

        result
    }

    #[inline]
    fn interpolate_linear(p0: StrokePoint, p1: StrokePoint, step_distance: f32) -> Vec<StrokePoint> {
        let dist = (p1.x - p0.x).hypot(p1.y - p0.y);
        let steps = 1.max((dist / step_distance) as usize);
        let inv_steps = 1.0 / (steps as f32);
        let mut list = Vec::with_capacity(steps + 1);

        for s in 0..=steps {
            let t = (s as f32) * inv_steps;
            let (tilt, orientation) = interpolate_tilt_orientation(p0, p1, t);
            list.push(StrokePoint {
                x: p0.x + (p1.x - p0.x) * t,
                y: p0.y + (p1.y - p0.y) * t,
                pressure: (p0.pressure + (p1.pressure - p0.pressure) * t).clamp(0.0, 1.0),
                time_millis: (p0.time_millis as f64 + (p1.time_millis - p0.time_millis) as f64 * t as f64) as i64,
                tilt,
                orientation,
                speed: (p0.speed + (p1.speed - p0.speed) * t).clamp(0.0, 1.0),
            });
        }
        list
    }

    #[inline(always)]
    fn evaluate(p0: StrokePoint, p1: StrokePoint, p2: StrokePoint, p3: StrokePoint, t: f32) -> StrokePoint {
        let t2 = t * t;
        let t3 = t2 * t;

        let f1 = -t3 + 2.0 * t2 - t;
        let f2 = 3.0 * t3 - 5.0 * t2 + 2.0;
        let f3 = -3.0 * t3 + 4.0 * t2 + t;
        let f4 = t3 - t2;

        let x = 0.5 * (p0.x * f1 + p1.x * f2 + p2.x * f3 + p3.x * f4);
        let y = 0.5 * (p0.y * f1 + p1.y * f2 + p2.y * f3 + p3.y * f4);
        let pressure = (0.5 * (p0.pressure * f1 + p1.pressure * f2 + p2.pressure * f3 + p3.pressure * f4)).clamp(0.0, 1.0);
        let time = (p1.time_millis as f64 + (p2.time_millis - p1.time_millis) as f64 * t as f64) as i64;
        let (tilt, orientation) = interpolate_tilt_orientation(p1, p2, t);
        let speed = (p1.speed + (p2.speed - p1.speed) * t).clamp(0.0, 1.0);

        StrokePoint {
            x,
            y,
            pressure,
            time_millis: time,
            tilt,
            orientation,
            speed,
        }
    }

    #[cfg(target_arch = "aarch64")]
    #[inline(always)]
    unsafe fn evaluate_4wide_neon(
        p0: StrokePoint,
        p1: StrokePoint,
        p2: StrokePoint,
        p3: StrokePoint,
        s_start: usize,
        inv_steps: f32,
    ) -> [StrokePoint; 4] {
        use std::arch::aarch64::*;

        let s_arr = [
            s_start as f32,
            (s_start + 1) as f32,
            (s_start + 2) as f32,
            (s_start + 3) as f32,
        ];
        let s_offsets = vld1q_f32(s_arr.as_ptr());
        let t = vmulq_n_f32(s_offsets, inv_steps);

        let t2 = vmulq_f32(t, t);
        let t3 = vmulq_f32(t2, t);

        let two = vdupq_n_f32(2.0);
        let three = vdupq_n_f32(3.0);
        let four = vdupq_n_f32(4.0);
        let five = vdupq_n_f32(5.0);

        let f1 = vsubq_f32(vmlaq_f32(vnegq_f32(t3), two, t2), t);
        let f2 = vaddq_f32(vsubq_f32(vmulq_f32(three, t3), vmulq_f32(five, t2)), two);
        let f3 = vaddq_f32(vsubq_f32(vmulq_f32(four, t2), vmulq_f32(three, t3)), t);
        let f4 = vsubq_f32(t3, t2);

        let half = vdupq_n_f32(0.5);

        let mut x_acc = vmulq_n_f32(f1, p0.x);
        x_acc = vmlaq_n_f32(x_acc, f2, p1.x);
        x_acc = vmlaq_n_f32(x_acc, f3, p2.x);
        x_acc = vmlaq_n_f32(x_acc, f4, p3.x);
        let x_res = vmulq_f32(x_acc, half);

        let mut y_acc = vmulq_n_f32(f1, p0.y);
        y_acc = vmlaq_n_f32(y_acc, f2, p1.y);
        y_acc = vmlaq_n_f32(y_acc, f3, p2.y);
        y_acc = vmlaq_n_f32(y_acc, f4, p3.y);
        let y_res = vmulq_f32(y_acc, half);

        let mut p_acc = vmulq_n_f32(f1, p0.pressure);
        p_acc = vmlaq_n_f32(p_acc, f2, p1.pressure);
        p_acc = vmlaq_n_f32(p_acc, f3, p2.pressure);
        p_acc = vmlaq_n_f32(p_acc, f4, p3.pressure);
        let p_half = vmulq_f32(p_acc, half);
        let p_res = vminq_f32(vmaxq_f32(p_half, vdupq_n_f32(0.0)), vdupq_n_f32(1.0));

        let mut x_out = [0.0f32; 4];
        let mut y_out = [0.0f32; 4];
        let mut p_out = [0.0f32; 4];

        vst1q_f32(x_out.as_mut_ptr(), x_res);
        vst1q_f32(y_out.as_mut_ptr(), y_res);
        vst1q_f32(p_out.as_mut_ptr(), p_res);

        let t0 = (s_start as f32) * inv_steps;
        let t1 = ((s_start + 1) as f32) * inv_steps;
        let t2_s = ((s_start + 2) as f32) * inv_steps;
        let t3_s = ((s_start + 3) as f32) * inv_steps;

        let (tilt0, ori0) = interpolate_tilt_orientation(p1, p2, t0);
        let (tilt1, ori1) = interpolate_tilt_orientation(p1, p2, t1);
        let (tilt2, ori2) = interpolate_tilt_orientation(p1, p2, t2_s);
        let (tilt3, ori3) = interpolate_tilt_orientation(p1, p2, t3_s);

        [
            StrokePoint::with_dynamics(x_out[0], y_out[0], p_out[0], p1.time_millis, tilt0, ori0, (p1.speed + (p2.speed - p1.speed) * t0).clamp(0.0, 1.0)),
            StrokePoint::with_dynamics(x_out[1], y_out[1], p_out[1], p1.time_millis, tilt1, ori1, (p1.speed + (p2.speed - p1.speed) * t1).clamp(0.0, 1.0)),
            StrokePoint::with_dynamics(x_out[2], y_out[2], p_out[2], p1.time_millis, tilt2, ori2, (p1.speed + (p2.speed - p1.speed) * t2_s).clamp(0.0, 1.0)),
            StrokePoint::with_dynamics(x_out[3], y_out[3], p_out[3], p1.time_millis, tilt3, ori3, (p1.speed + (p2.speed - p1.speed) * t3_s).clamp(0.0, 1.0)),
        ]
    }
}
