package com.example.implicit

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.util.AttributeSet
import android.view.*
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import java.lang.Math.pow
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.ArrayList
import kotlin.math.*

class CanvasView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0): View(context, attrs, defStyleAttr) {
    private lateinit var canvas: Canvas
    private lateinit var bitmap: Bitmap
    private val backgroundColor = ResourcesCompat.getColor(resources, R.color.colorBackground, null)
    private val drawColor = ResourcesCompat.getColor(resources, R.color.colorPaint, null)
    private val gridColor = ResourcesCompat.getColor(resources, R.color.gridColor, null)
    private val textColor = ResourcesCompat.getColor(resources, R.color.paintColor, null)
    private var equation: String = "1"
    private lateinit var frame: Rect
    private var lines: ArrayDeque<Line> = ArrayDeque()
    private var x1 = -1.0
    private var x2 = 1.0
    private var y1 = -0.4
    private var y2 = 1.6
    private var original_x1 = x1
    private var original_x2 = x2
    private var original_y1 = y1
    private var original_y2 = y2
    private var currentX = 0f
    private var currentY = 0f
    private val touchTolerance = ViewConfiguration.get(context).scaledTouchSlop

    private fun Double.format(n: Int): String {
        if (n >= 0)
            return this.toInt().toString()

        val df = DecimalFormat("#."+"#".repeat(-n))
        df.roundingMode = RoundingMode.HALF_DOWN
        return if(df.format(this).toString() == "-0") {
            "0"
        } else {
            df.format(this).toString()
        }
    }

    private val paint = Paint().apply {
        color = drawColor
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3f
    }

    private val textPaint = Paint().apply {
        textSize = 20.0f
        color = textColor
        strokeWidth = 1f
    }

    private val gridPaint = Paint().apply {
        color = gridColor
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3f
    }


    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        if (::bitmap.isInitialized) bitmap.recycle()
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)

        val inset = 40
        frame = Rect(inset, inset, width - inset, height - inset)
    }

    private fun checkUpdate() {
        if (x1 < 2*original_x1-original_x2 || x2 > 2*original_x2-original_x1 || y1 < 2*original_y1-original_y2 || y2 > 2*original_y2-original_y1
            || (x2-x1)*1.5 < original_x2-original_x1 || (y2-y1)*1.5 < original_y2-original_y1) {
            original_x1 = x1
            original_x2 = x2
            original_y1 = y1
            original_y2 = y2
            val thread = Thread {
                setGraph(equation)
            }
            thread.start()
            return
        }
    }

    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val width_x = x2-x1
            val width_y = y2-y1

            x1 -= (detector.focusX / width) * width_x * (1-detector.scaleFactor)
            x2 += (1 - detector.focusX / width) * width_x * (1-detector.scaleFactor)
            y1 -= detector.focusY / height * width_y * (1-detector.scaleFactor)
            y2 += (1 - detector.focusY / height) * width_y * (1-detector.scaleFactor)
            checkUpdate()

            invalidate()
            return true
        }
    }

    private val mScaleDetector = ScaleGestureDetector(context, scaleListener)
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val motionTouchEventX = event.x
        val motionTouchEventY = event.y

        mScaleDetector.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_MOVE && !mScaleDetector.isInProgress) {
            val dx = motionTouchEventX - currentX
            val dy = motionTouchEventY - currentY
            if ((abs(dx) >= touchTolerance || abs(dy) >= touchTolerance) && (abs(dx) < 5*touchTolerance || abs(dy) < 5*touchTolerance)) {
                currentX = motionTouchEventX
                currentY = motionTouchEventY

                val width_x = (x2-x1)
                val width_y = (y2-y1)
                x1 -= dx / width * width_x
                x2 -= dx / width * width_x
                y1 += dy / width * width_y
                y2 += dy / width * width_y

                checkUpdate()
                invalidate()
                return true
            }
            else {
                return false
            }
        }
        if (event.action == MotionEvent.ACTION_DOWN && !mScaleDetector.isInProgress) {
            currentX = motionTouchEventX
            currentY = motionTouchEventY
            return true
        }
        if (event.action == MotionEvent.ACTION_UP && !mScaleDetector.isInProgress) {
            val thread = Thread {
                setGraph(equation)
            }
            thread.start()
        }
        return true
    }

    fun setGraph(equation: String) {
        this.equation = equation
        var y1 = y1
        if (width != 0 && height != 0)
            y1 = y2 - (x2 - x1) * height / width
        val dx = min(x2-x1, y2-y1) / 200
        try {
            lines = Graph(x1, x2, y1, y2, dx, dx, dx, equation).create()
        } catch(e: Exception) {
            ContextCompat.getMainExecutor(context).execute{
                val toast = Toast.makeText(context, e.message, Toast.LENGTH_LONG)
                toast.show()
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        val afterLeadingDigit = floor(log(x2-x1, 10.0))
        val firstDigit = floor((x2-x1)/ 10.0.pow(afterLeadingDigit))
        val unit = firstDigit * pow(10.0, afterLeadingDigit-1) * 2
        var x = ceil(x1/unit)*unit
        var y = ceil((y1-(y2-y1)*height/width)/unit)*unit

        while(x < x2) {
            val device_x = (x - x1) * width / (x2-x1)
            canvas.drawLine(device_x.toFloat(), 0.0f, device_x.toFloat(), height.toFloat(), gridPaint)
            canvas.drawText(x.format(afterLeadingDigit.toInt()-1), device_x.toFloat() + 5, 20.0f, textPaint)
            x += unit
        }
        while(y < y2) {
            val device_y = width - (y - y1) * width / (y2-y1)
            canvas.drawLine(0.0f, device_y.toFloat(), width.toFloat(), device_y.toFloat(), gridPaint)
            canvas.drawText(y.format(afterLeadingDigit.toInt()-1), 0.0f, device_y.toFloat() - 5, textPaint)
            y += unit
        }

        for(line in lines) {
            val device_x1 = (line.first.x - x1) * width / (x2 - x1)
            val device_x2 = (line.second.x - x1) * width / (x2 - x1)
            val device_y1 = width - (line.first.y - y1) * width / (y2 - y1)
            val device_y2 = width - (line.second.y - y1) * width / (y2 - y1)
            canvas.drawLine(device_x1.toFloat(), device_y1.toFloat(), device_x2.toFloat(), device_y2.toFloat(), paint)
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelableArrayList("LINES", ArrayList<Line>(lines))
        bundle.putParcelable("superState", super.onSaveInstanceState())
        bundle.putDouble("x1", x1)
        bundle.putDouble("x2", x2)
        bundle.putDouble("y1", y1)
        bundle.putDouble("y2", y2)
        bundle.putString("equation", equation)
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        var viewState = state

        if (viewState is Bundle) {
            val array = viewState.getParcelableArrayList<Line>("LINES")
            x1 = viewState.getDouble("x1")
            x2 = viewState.getDouble("x2")
            y1 = viewState.getDouble("y1")
            y2 = viewState.getDouble("y2")
            equation = viewState.getString("equation", "1")
            lines = ArrayDeque()
            array?.forEach{item -> lines.add(item)}
            viewState = viewState.getParcelable("superState")
        }
        super.onRestoreInstanceState(viewState)
        invalidate()
    }
}