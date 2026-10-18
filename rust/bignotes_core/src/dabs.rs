use crate::spline::StrokePoint;

pub struct DabGenerator {
    step_distance: f32,
    last_x: f32,
    last_y: f32,
    last_pressure: f32,
    dist_to_next_dab: f32,
    has_started: bool,
}

impl DabGenerator {
    pub fn new(step_distance: f32) -> Self {
        Self {
            step_distance,
            last_x: 0.0,
            last_y: 0.0,
            last_pressure: 0.0,
            dist_to_next_dab: 0.0,
            has_started: false,
        }
    }

    pub fn start(&mut self, x: f32, y: f32, pressure: f32, out: &mut Vec<StrokePoint>) {
        self.last_x = x;
        self.last_y = y;
        self.last_pressure = pressure;
        self.dist_to_next_dab = self.step_distance;
        self.has_started = true;
        out.push(StrokePoint::new(x, y, pressure, 0));
    }

    pub fn add_point(&mut self, x: f32, y: f32, pressure: f32, out: &mut Vec<StrokePoint>) {
        if !self.has_started {
            self.start(x, y, pressure, out);
            return;
        }

        let dx = x - self.last_x;
        let dy = y - self.last_y;
        let seg_dist = dx.hypot(dy);
        if seg_dist < 1e-4 {
            return;
        }

        let inv_seg = 1.0 / seg_dist;
        let mut cur_dist = self.dist_to_next_dab;

        while cur_dist <= seg_dist {
            let t = cur_dist * inv_seg;
            let dab_x = self.last_x + dx * t;
            let dab_y = self.last_y + dy * t;
            let dab_p = self.last_pressure + (pressure - self.last_pressure) * t;
            out.push(StrokePoint::new(dab_x, dab_y, dab_p, 0));
            cur_dist += self.step_distance;
        }

        self.dist_to_next_dab = cur_dist - seg_dist;
        self.last_x = x;
        self.last_y = y;
        self.last_pressure = pressure;
    }
}
