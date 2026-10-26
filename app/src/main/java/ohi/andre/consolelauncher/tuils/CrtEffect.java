package ohi.andre.consolelauncher.tuils;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Old monitor look: horizontal scanlines, darker corners and glowing text.
 */

public class CrtEffect {

    private static final float GLOW_RADIUS = 8f;

    public static void apply(ViewGroup root) {
        final ScanlinesDrawable scanlines = new ScanlinesDrawable(root.getResources().getDisplayMetrics().density);
        root.getOverlay().add(scanlines);
        scanlines.setBounds(0, 0, root.getWidth(), root.getHeight());
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> scanlines.setBounds(0, 0, r - l, b - t));
    }

    public static void glow(TextView tv, int color) {
        if (tv == null) return;
        tv.setShadowLayer(GLOW_RADIUS, 0, 0, (color & 0x00ffffff) | 0xb0000000);
    }

    private static class ScanlinesDrawable extends Drawable {

        private final Paint line = new Paint();
        private final Paint vignette = new Paint();
        private final float spacing;

        ScanlinesDrawable(float density) {
            line.setColor(0x38000000);
            line.setStrokeWidth(Math.max(1f, density));
            spacing = Math.max(3f, 3f * density);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            float radius = (float) Math.hypot(bounds.width(), bounds.height()) / 2f;
            if (radius <= 0) return;
            vignette.setShader(new RadialGradient(bounds.exactCenterX(), bounds.exactCenterY(), radius,
                    new int[] {0x00000000, 0x00000000, 0x70000000}, new float[] {0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            for (float y = b.top; y < b.bottom; y += spacing) canvas.drawLine(b.left, y, b.right, y, line);
            canvas.drawRect(b, vignette);
        }

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
