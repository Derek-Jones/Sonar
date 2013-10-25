package com.dicon.sonar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Paint.Align;
import android.util.AttributeSet;
import android.view.View;

public class GraphView extends View {
	float[][] graph = new float[5][];
	int size = 0;
	float maxRange;
	float minRange;
	float[][] dist;
	String unit = "m";
	float[] limit = new float[5];
	float norm = 0;
	public int showPrev = 5;
	
	public GraphView(Context context, AttributeSet attr) {
		super(context, attr);
	}
	
	public void setGraph(float[] graph, int size, float minRange, float maxRange, float[][]dist) {
		norm = 0;
		for (int i = 4; i > 0; i--) {
			this.graph[i] = this.graph[i-1];
			limit[i] = limit[i-1];
			norm = Math.max(norm, limit[i]);
		}
		this.graph[0] = graph.clone();
		limit[0] = dist[0][1];
		norm = Math.max(norm, limit[0]);
		this.size = size;
		this.minRange = minRange;
		this.maxRange = maxRange;
		this.dist = dist;
		this.invalidate();
	}
	
	public void setUnit(String newUnit) {
		unit = newUnit;
		this.size = 0;
		for (int i = 0; i < 5; i++) {
			this.graph[i] = null;
			limit[i] = 0;
		}
		this.invalidate();
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setTextSize(20.f);
		paint.setTextAlign(Align.CENTER);
		
		int w = this.getWidth();
		int h = this.getHeight();
		
		if (size > 0) {
			paint.setStrokeWidth(3);
			for (int j = showPrev; j >= 0; j--) {
				if (graph[j] != null) {
					if (j == 0) {
						paint.setStrokeWidth(3);
						paint.setColor(0xff33ff33);
					} else {
						paint.setStrokeWidth(2);
						paint.setColor(0x00ffffff | (((5-j)*255/5) << 24));
					}
					for (int x = 0; x < size/3; x++) {
						canvas.drawPoint(x*w*3/size, (int)((1.f-graph[j][x]/norm) * h/2)+h/2, paint);
					}
				}
			}
			paint.setColor(0xff808080);
			paint.setStrokeWidth(1);
			canvas.drawLine(0, h-1, w-1, h-1, paint);
			int step = 1;
			if (maxRange/3.f > 200) {
				step = 100;
			} else if (maxRange/3.f > 20) {
				step = 10;
			}
			for (int m = step; m < maxRange*10/3.; m+= step) {
				if (m % (10*step) == 0)
					canvas.drawLine(m*w*3/maxRange/10.f, h*3/4, m*w*3/maxRange/10.f, h-1, paint);
				else
					canvas.drawLine(m*w*3/maxRange/10.f, h*7/8, m*w*3/maxRange/10.f, h-1, paint);
			}
			
			for (int i = 0; i < 5; i++) {
				if (dist[i][0] > 0) {
					paint.setColor((int)(dist[i][1]*255)*0x01000000 | 0x00ffffff);
					canvas.drawText(((Float)(Math.round(dist[i][0]*100)/100.f)).toString()+unit, dist[i][0]*w*3/maxRange, (1.f-dist[i][1]/norm)*h/2+h/4, paint);
					canvas.drawLine(dist[i][0]*w*3/maxRange, (1.f-dist[i][1]/norm)*h/2+h/4, dist[i][0]*w*3/maxRange, (int)((1.f-dist[i][1]/norm) * h/2)+h/2, paint);
				}
			}
			
			paint.setColor(0x80800000);
			paint.setStyle(Paint.Style.FILL);
			canvas.drawRect(new Rect(0, 0, (int)(minRange*w*3/maxRange), h-1), paint);
		} else {
			paint.setColor(0xffffffff);
			canvas.drawText("Press \"Ping\"...", w/2, h/2, paint);
			canvas.drawText("Or get instructions from the menu.", w/2, h*3/4, paint);
		}
	}
}
