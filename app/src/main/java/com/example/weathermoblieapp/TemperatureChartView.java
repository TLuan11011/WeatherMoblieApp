package com.example.weathermoblieapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.List;

public class TemperatureChartView extends View {

    private List<Float> temperatures;
    private List<Float> rainProbs;

    private float itemWidthPx;
    private int scrollOffset = 0;

    private final Paint linePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rainPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dropPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TemperatureChartView(Context context) {
        super(context);
        init();
    }

    public TemperatureChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        float dp = getResources().getDisplayMetrics().density;
        // Mỗi item: width=70dp + margin 6dp*2 = 82dp
        itemWidthPx = 82 * dp;

        linePaint.setColor(Color.WHITE);
        linePaint.setStrokeWidth(2.5f * dp);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        dotPaint.setColor(Color.WHITE);
        dotPaint.setStyle(Paint.Style.FILL);

        fillPaint.setColor(Color.parseColor("#33FFFFFF"));
        fillPaint.setStyle(Paint.Style.FILL);

        rainPaint.setColor(Color.parseColor("#80AADDFF"));
        rainPaint.setTextSize(10 * dp);
        rainPaint.setTextAlign(Paint.Align.CENTER);

        dropPaint.setColor(Color.parseColor("#80AADDFF"));
        dropPaint.setStyle(Paint.Style.FILL);
    }

    public void setData(List<Float> temps, List<Float> probs) {
        this.temperatures = temps;
        this.rainProbs    = probs;
        invalidate();
    }

    public void setScrollOffset(int offset) {
        this.scrollOffset = offset;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (temperatures == null || temperatures.size() < 2) return;

        float dp          = getResources().getDisplayMetrics().density;
        int   viewWidth   = getWidth();
        int   viewHeight  = getHeight();

        float rainAreaH   = 22 * dp;   // vùng hiển thị % mưa bên dưới
        float dotRadius   = 4  * dp;
        float topPad      = dotRadius + 6 * dp;
        float chartH      = viewHeight - rainAreaH - topPad - dotRadius;

        int n = temperatures.size();

        // Tìm min / max
        float minT = temperatures.get(0), maxT = temperatures.get(0);
        for (float t : temperatures) {
            if (t < minT) minT = t;
            if (t > maxT) maxT = t;
        }
        if (maxT == minT) { maxT += 1; minT -= 1; }

        // Toạ độ từng điểm
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = itemWidthPx * i + itemWidthPx / 2f - scrollOffset;
            ys[i] = topPad + (maxT - temperatures.get(i)) / (maxT - minT) * chartH;
        }

        // Clip để không vẽ ra ngoài view
        canvas.clipRect(0, 0, viewWidth, viewHeight);

        // ── Vùng tô bên dưới đường ──
        Path fill = new Path();
        fill.moveTo(xs[0], viewHeight - rainAreaH);
        fill.lineTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) {
            float cx = (xs[i - 1] + xs[i]) / 2f;
            fill.cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i]);
        }
        fill.lineTo(xs[n - 1], viewHeight - rainAreaH);
        fill.close();
        canvas.drawPath(fill, fillPaint);

        // ── Đường line ──
        Path line = new Path();
        line.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) {
            float cx = (xs[i - 1] + xs[i]) / 2f;
            line.cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i]);
        }
        canvas.drawPath(line, linePaint);

        // ── Chấm tròn trên đường ──
        for (int i = 0; i < n; i++) {
            canvas.drawCircle(xs[i], ys[i], dotRadius, dotPaint);
        }

        // ── Xác suất mưa ──
        if (rainProbs != null) {
            for (int i = 0; i < Math.min(n, rainProbs.size()); i++) {
                int pct = Math.round(rainProbs.get(i) * 100);
                // Biểu tượng giọt nước nhỏ
                float dropX = xs[i] - 10 * dp;
                float dropY = viewHeight - 6 * dp;
                canvas.drawCircle(dropX, dropY - 3 * dp, 3 * dp, dropPaint);
                canvas.drawText(pct + "%", xs[i] + 4 * dp, dropY, rainPaint);
            }
        }
    }
}