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

            for s in 1..=steps {
                let t = (s as f32) * inv_steps;
                let interpolated = Self::evaluate(p0, p1, p2, p3, t);
                result.push(interpolated);
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
}
