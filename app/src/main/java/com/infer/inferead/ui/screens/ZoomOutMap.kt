package com.infer.inferead.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

public val ZoomOutMapIcon: ImageVector
  get() {
    if (_zoom_out_map != null) {
      return _zoom_out_map!!
    }
    _zoom_out_map =
      ImageVector.Builder(
          name = "zoom_out_map",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(3f, 21f)
            verticalLineTo(15f)
            horizontalLineTo(5f)
            verticalLineToRelative(2.6f)
            lineTo(8.1f, 14.5f)
            lineToRelative(1.4f, 1.4f)
            lineTo(6.4f, 19f)
            horizontalLineTo(9f)
            verticalLineToRelative(2f)
            horizontalLineTo(3f)
            close()
            moveToRelative(12f, 0f)
            verticalLineTo(19f)
            horizontalLineToRelative(2.6f)
            lineTo(14.5f, 15.9f)
            lineToRelative(1.4f, -1.4f)
            lineTo(19f, 17.6f)
            verticalLineTo(15f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(6f)
            horizontalLineTo(15f)
            close()
            moveTo(8.1f, 9.5f)
            lineTo(5f, 6.4f)
            verticalLineTo(9f)
            horizontalLineTo(3f)
            verticalLineTo(3f)
            horizontalLineTo(9f)
            verticalLineTo(5f)
            horizontalLineTo(6.4f)
            lineTo(9.5f, 8.1f)
            lineTo(8.1f, 9.5f)
            close()
            moveToRelative(7.8f, 0f)
            lineTo(14.5f, 8.1f)
            lineTo(17.6f, 5f)
            horizontalLineTo(15f)
            verticalLineTo(3f)
            horizontalLineToRelative(6f)
            verticalLineTo(9f)
            horizontalLineTo(19f)
            verticalLineTo(6.4f)
            lineTo(15.9f, 9.5f)
            close()
          }
        }
        .build()
    return _zoom_out_map!!
  }

private var _zoom_out_map: ImageVector? = null
