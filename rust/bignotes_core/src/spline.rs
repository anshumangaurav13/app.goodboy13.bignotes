#[derive(Debug, Clone, Copy, PartialEq)]
pub struct StrokePoint {
    pub x: f32,
    pub y: f32,
    pub pressure: f32,
    pub time_millis: i64,
}

impl StrokePoint {
    #[inline(always)]
    pub fn new(x: f32, y: f32, pressure: f32, time_millis: i64) -> Self {
        Self {
            x,
            y,
            pressure,
            time_millis,
        }
    }
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
            list.push(StrokePoint {
                x: p0.x + (p1.x - p0.x) * t,
                y: p0.y + (p1.y - p0.y) * t,
                pressure: p0.pressure + (p1.pressure - p0.pressure) * t,
                time_millis: (p0.time_millis as f64 + (p1.time_millis - p0.time_millis) as f64 * t as f64) as i64,
            });
        }
        list
    }

    #[inline(always)]
    fn evaluate(p0: StrokePoint, p1: StrokePoint, p2: StrokePoint, p3: StrokePoint, t: f32) -> StrokePoint {
        let t2 = t * t;
        let t3 = t2 * t;

        // Standard Catmull-Rom basis:
        // q(t) = 0.5 * ((2*p1) + (-p0 + p2)*t + (2*p0 - 5*p1 + 4*p2 - p3)*t^2 + (-p0 + 3*p1 - 3*p2 + p3)*t^3)
        let f1 = -t3 + 2.0 * t2 - t;
        let f2 = 3.0 * t3 - 5.0 * t2 + 2.0;
        let f3 = -3.0 * t3 + 4.0 * t2 + t;
        let f4 = t3 - t2;

        let x = 0.5 * (p0.x * f1 + p1.x * f2 + p2.x * f3 + p3.x * f4);
        let y = 0.5 * (p0.y * f1 + p1.y * f2 + p2.y * f3 + p3.y * f4);
        let pressure = (0.5 * (p0.pressure * f1 + p1.pressure * f2 + p2.pressure * f3 + p3.pressure * f4)).clamp(0.0, 1.0);
        let time = (0.5 * (p0.time_millis as f32 * f1 + p1.time_millis as f32 * f2 + p2.time_millis as f32 * f3 + p3.time_millis as f32 * f4)) as i64;

        StrokePoint {
            x,
            y,
            pressure,
            time_millis: time,
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

        // f1 = -t3 + 2*t2 - t
        let f1 = vsubq_f32(vmlaq_f32(vnegq_f32(t3), two, t2), t);
        // f2 = 3*t3 - 5*t2 + 2
        let f2 = vaddq_f32(vsubq_f32(vmulq_f32(three, t3), vmulq_f32(five, t2)), two);
        // f3 = -3*t3 + 4*t2 + t
        let f3 = vaddq_f32(vsubq_f32(vmulq_f32(four, t2), vmulq_f32(three, t3)), t);
        // f4 = t3 - t2
        let f4 = vsubq_f32(t3, t2);

        let half = vdupq_n_f32(0.5);

        // x
        let mut x_acc = vmulq_n_f32(f1, p0.x);
        x_acc = vmlaq_n_f32(x_acc, f2, p1.x);
        x_acc = vmlaq_n_f32(x_acc, f3, p2.x);
        x_acc = vmlaq_n_f32(x_acc, f4, p3.x);
        let x_res = vmulq_f32(x_acc, half);

        // y
        let mut y_acc = vmulq_n_f32(f1, p0.y);
        y_acc = vmlaq_n_f32(y_acc, f2, p1.y);
        y_acc = vmlaq_n_f32(y_acc, f3, p2.y);
        y_acc = vmlaq_n_f32(y_acc, f4, p3.y);
        let y_res = vmulq_f32(y_acc, half);

        // pressure
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

        [
            StrokePoint::new(x_out[0], y_out[0], p_out[0], p1.time_millis),
            StrokePoint::new(x_out[1], y_out[1], p_out[1], p1.time_millis),
            StrokePoint::new(x_out[2], y_out[2], p_out[2], p1.time_millis),
            StrokePoint::new(x_out[3], y_out[3], p_out[3], p1.time_millis),
        ]
    }
}
