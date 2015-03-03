package com.chedima.btscaleviewer;


import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;


public class ScaleView extends View {

	//Текущее положение
	//Значения от 0 до 60 лежат в начальной зоне, которая имеет зеленый цвет.
	//Значения от 61 до 100 лежат в средней зоне, которая имеет желтый цвет.
	//Значения от 101 до 253 лежат в конечной зоне, которая имеет красный цвет.
	//255 = данных пока не поступало
	public int currPos=255;
	
	boolean inverted = false;
	boolean showHistory = true;
	private Drawable shape;
	private Paint currPaint = new Paint();
	private Paint textPaint = new Paint(); 
	private Paint whitePaint = new Paint();
	private Paint blackPaint = new Paint();
	private Paint pathPaint = new Paint();
	private Rect textBounds = new Rect();
	private int h,w;
	private List<Integer> histData = new ArrayList<Integer>();
  	
	public final static int NOTCONNECTED = 255;
	
	public ScaleView(Context context) {
		super(context);
		SetupGraphics();
	}

	public ScaleView (Context context, AttributeSet ats, int defaultStyle) {
		super(context, ats, defaultStyle );
		SetupGraphics();
	}

	public ScaleView (Context context, AttributeSet attrs) {
		super(context, attrs);
		SetupGraphics();
	}
	
	private void SetupGraphics()
	{
		shape = this.getResources().getDrawable(R.drawable.gradient);
		// перегоним dp в пикселы. формула  px = dp * (dpi / 160), так что для 
		textPaint.setTextSize(dipToPixels(120));
		blackPaint.setColor(inverted? Color.WHITE : Color.BLACK);
		whitePaint.setColor(inverted? Color.BLACK : Color.WHITE);
		pathPaint.setColor(Color.MAGENTA);
		blackPaint.setTextSize(dipToPixels(15));
		pathPaint.setStyle(Paint.Style.STROKE);
		pathPaint.setStrokeWidth(3);
		textPaint.getTextBounds("200", 0, 3, textBounds);
	
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		canvas.drawColor(whitePaint.getColor());
	    shape.setBounds(0,(int)(h*0.75),w,h);
        shape.draw(canvas);
        
        if (showHistory){
        histData.add(currPos);
        
        for (int i=2;i<=histData.size() && i<this.w;i++){
        	canvas.drawLine(i-1,255-histData.get(histData.size()-i+1), i, 255-histData.get(histData.size()-i), pathPaint);
        }
        canvas.drawLine(0, 175, w, 175, blackPaint); // 255-80=175, это среднее желтое значение
        //canvas.drawText("80", 10, dipToPixels(100) ,blackPaint);
        }
            
        
        if (currPos<61)
        	currPaint.setColor(Color.GREEN);
        else if (currPos<101)
        	currPaint.setColor(Color.YELLOW);
        else 
        	currPaint.setColor(Color.RED);
        

        int x = (int)(w*currPos/253.0);
        
        canvas.drawRect(x, (int)(h*0.75), w, h, whitePaint );
		canvas.drawRect(x-4,(int)(h*0.75)-15, x+4, h, blackPaint );

	   	textPaint.setColor(currPos>100? Color.RED : blackPaint.getColor());
	   	
	   	String txt = currPos!=NOTCONNECTED? Integer.toString(currPos) : "--";
	   	
    	//canvas.drawText(txt,x<50? x: (x>w-410?w-410:x-50),	 dipToPixels(140), textPaint);
	   	canvas.drawText(txt, (float)((w - textBounds.width()) >> 1),	dipToPixels(120), textPaint);
    	
    	

	}

	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
	
		if (event.getX()>w-dipToPixels(80) && event.getY()<dipToPixels(80)) {
			inverted = !inverted;
			SetupGraphics();
			invalidate();
		} else if (event.getX()>w-dipToPixels(80) && event.getY()>h-dipToPixels(80))
		{
			showHistory = !showHistory; 
			invalidate();
		} else
		{			
			currPos = (int) (((event.getX())/w)*253.0);
			invalidate();
		}
		return super.onTouchEvent(event);
	}

	private float dipToPixels(int px){
		return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, px, getResources().getDisplayMetrics());
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		this.w = w;
		this.h = h;
		super.onSizeChanged(w, h, oldw, oldh);
	}
	
	
	
//	// Класс следов линий, для контроля динамики разгона/торможения
//	public class LineTraces {
//		private static final long GLOWTIME = 1;
//		private LineTrace[] traces; 
//		
//		LineTraces(int l){
//			traces = new LineTrace[l];
//			for (int i=0; i < l; i++){
//				traces[i] = new LineTrace(0);
//			}
//		}
//
//		public int getLength (){
//			return traces.length;
//		}
//		
//		public void setLineTrace (int pos, double val){
//			traces[pos] = new LineTrace(val);
//		}
//		
//		public double getLineTrace (int pos){
//			if (getLineTraceAge(pos)==255) 
//				setLineTrace(pos,0);
//			return traces[pos].value;
//		}
//
//		public int getLineTraceAge (int position){
//			// "Возраст" следа - диапазон от 0 (молодой) до 255 (максимум)
//			return traces[position].howOldAmI();
//		}
//		
//		private class LineTrace{
//
//			double value;
//			private long birthMSec;
//
//			LineTrace(double v){
//				value = v;
//				birthMSec = System.currentTimeMillis();
//			}
//			
//			
//			public int howOldAmI (){
//				int toRet = (int) ((System.currentTimeMillis()-birthMSec)/1000d/GLOWTIME*255d);
//				return toRet>255 ? 255 : toRet; 
//			}
//		}
//	}
}

