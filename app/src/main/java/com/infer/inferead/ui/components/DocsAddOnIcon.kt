package com.infer.inferead.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val docs_add_on: ImageVector
  get() {
    if (_docs_add_on != null) {
      return _docs_add_on!!
    }
    _docs_add_on =
      ImageVector.Builder(
          name = "docs_add_on",
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
            moveTo(16f, 20.98f)
            verticalLineToRelative(-3f)
            horizontalLineTo(13f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(-3f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(2f)
            horizontalLineTo(18f)
            verticalLineToRelative(3f)
            horizontalLineTo(16f)
            close()
            moveTo(4f, 18f)
            verticalLineTo(16f)
            horizontalLineToRelative(7.08f)
            quadTo(11f, 16.52f, 11.01f, 17f)
            reflectiveQuadToRelative(0.09f, 1f)
            horizontalLineTo(4f)
            close()
            moveTo(4f, 14f)
            verticalLineTo(12f)
            horizontalLineToRelative(9.65f)
            quadToRelative(-0.57f, 0.4f, -1.04f, 0.9f)
            reflectiveQuadTo(11.8f, 14f)
            horizontalLineTo(4f)
            close()
            moveTo(4f, 10f)
            verticalLineTo(8f)
            horizontalLineTo(19f)
            verticalLineToRelative(2f)
            horizontalLineTo(4f)
            close()
            moveTo(4f, 6f)
            verticalLineTo(4f)
            horizontalLineTo(19f)
            verticalLineTo(6f)
            horizontalLineTo(4f)
            close()
          }
        }
        .build()
    return _docs_add_on!!
  }

private var _docs_add_on: ImageVector? = null
