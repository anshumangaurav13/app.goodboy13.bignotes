pub mod dabs;
pub mod spline;
pub mod tessellator;

use jni::objects::{JClass, JFloatArray};
use jni::sys::{jboolean, jfloat, jfloatArray, jint, jlong, JNI_TRUE};
use jni::JNIEnv;

use crate::spline::StrokePoint;
use crate::tessellator::tessellate_stroke;

#[no_mangle]
pub extern "system" fn Java_app_goodboy13_bignotes_core_geometry_NativeStrokeTessellator_nativeTessellateStroke(
    env: JNIEnv,
    _class: JClass,
    points_array: JFloatArray,
    point_count: jint,
    base_width: jfloat,
    color_rgb: jlong,
    is_ink: jboolean,
    is_highlighter: jboolean,
    quad_margin_ratio: jfloat,
) -> jfloatArray {
    if point_count <= 0 || points_array.is_null() {
        return std::ptr::null_mut();
    }

    let required_floats = (point_count as usize) * 3;
    let mut raw_points = vec![0.0f32; required_floats];

    if env
        .get_float_array_region(&points_array, 0, &mut raw_points)
        .is_err()
    {
        return std::ptr::null_mut();
    }

    let mut points = Vec::with_capacity(point_count as usize);
    for chunk in raw_points.chunks_exact(3) {
        points.push(StrokePoint::new(chunk[0], chunk[1], chunk[2], 0));
    }

    let is_ink_bool = is_ink == JNI_TRUE;
    let is_hl_bool = is_highlighter == JNI_TRUE;

    let output_floats = tessellate_stroke(
        &points,
        base_width,
        color_rgb,
        is_ink_bool,
        is_hl_bool,
        quad_margin_ratio,
    );

    if output_floats.is_empty() {
        return std::ptr::null_mut();
    }

    let result_array = match env.new_float_array(output_floats.len() as jint) {
        Ok(arr) => arr,
        Err(_) => return std::ptr::null_mut(),
    };

    if env
        .set_float_array_region(&result_array, 0, &output_floats)
        .is_err()
    {
        return std::ptr::null_mut();
    }

    result_array.into_raw()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::spline::CatmullRomSpline;
    use crate::tessellator::{FLOATS_PER_VERTEX, VERTICES_PER_DAB};

    #[test]
    fn test_empty_stroke() {
        let out = tessellate_stroke(&[], 5.0, 0xFF000000, true, false, 0.25);
        assert!(out.is_empty());
    }

    #[test]
    fn test_single_point_stroke() {
        let pts = vec![StrokePoint::new(10.0, 20.0, 0.5, 0)];
        let out = tessellate_stroke(&pts, 5.0, 0xFF000000, true, false, 0.25);
        // Single point produces 1 dab = 6 vertices * 10 floats = 60 floats
        assert_eq!(out.len(), VERTICES_PER_DAB * FLOATS_PER_VERTEX);
        assert_eq!(out[0], 10.0);
        assert_eq!(out[1], 20.0);
    }

    #[test]
    fn test_two_point_stroke() {
        let pts = vec![
            StrokePoint::new(0.0, 0.0, 0.5, 0),
            StrokePoint::new(10.0, 0.0, 0.5, 0),
        ];
        let out = tessellate_stroke(&pts, 5.0, 0xFF000000, true, false, 0.25);
        assert!(!out.is_empty());
        assert_eq!(out.len() % (VERTICES_PER_DAB * FLOATS_PER_VERTEX), 0);
    }

    #[test]
    fn test_spline_interpolation() {
        let pts = vec![
            StrokePoint::new(0.0, 0.0, 0.5, 0),
            StrokePoint::new(10.0, 10.0, 0.5, 10),
            StrokePoint::new(20.0, 0.0, 0.5, 20),
        ];
        let interpolated = CatmullRomSpline::interpolate(&pts, 2.0);
        assert!(interpolated.len() > pts.len());
    }
}
