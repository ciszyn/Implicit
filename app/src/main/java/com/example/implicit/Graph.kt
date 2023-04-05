package com.example.implicit

import android.os.Parcel
import android.os.Parcelable
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

class Node(var x: Double, var y: Double)
class Line(var first: Node, var second: Node) : Parcelable {
    constructor(parcel: Parcel) : this(Node(0.0, 0.0), Node(0.0, 0.0)) {
        first.x = parcel.readDouble()
        first.y = parcel.readDouble()
        second.x = parcel.readDouble()
        second.y = parcel.readDouble()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeDouble(first.x)
        parcel.writeDouble(first.y)
        parcel.writeDouble(second.x)
        parcel.writeDouble(second.y)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Line> {
        override fun createFromParcel(parcel: Parcel): Line = Line(parcel)

        override fun newArray(size: Int): Array<Line?> = arrayOfNulls(size)
    }

}

class Graph(val x1: Double, val x2: Double, val y1: Double, val y2: Double, val dx: Double, val dy: Double, val dl: Double, val equation: String) {
    private var expression: Expression = Expression(equation.replace("=", "-"))
    private var gradient: Pair<Expression, Expression> = Pair(expression.derivative("x"), expression.derivative("y"))
    private var columns: Int = kotlin.math.ceil((y2 - y1) / dy).toInt() + 1
    private var rows: Int = kotlin.math.ceil((x2 - x1) / dx).toInt() + 1

    private fun newtonMethod(x: Node, f: Expression, gradient: Pair<Expression, Expression>, dx: Double, epsilon: Double = 1e-10): Node {
        var x0 = Node(x.x, x.y)
        val original = Node(x.x, x.y)

        while(true) {
            var z = f.evaluate(mapOf("x" to x.x, "y" to x.y))
            if (abs(z) < epsilon)
                return x
            if (abs(x.x - original.x) > dx || abs(x.y - original.y) > dx)
                return x0

            x0.x = x.x
            x0.y = x.y

            val grad_x = gradient.first.evaluate(mapOf("x" to x.x, "y" to x.y))
            val grad_y = gradient.second.evaluate(mapOf("x" to x.x, "y" to x.y))
            val norm = grad_x * grad_x + grad_y * grad_y

            if(norm.isNaN() || z.isNaN())
                return x


            x.x -= z * grad_x / norm
            x.y -= z * grad_y / norm
        }
    }

    private fun differentSign(a: Double, b: Double): Boolean {
        if(a.isInfinite() || a.isNaN() || b.isInfinite() || b.isNaN())
        {
            return false
        }
        return a.sign != b.sign
    }

    fun create(): ArrayDeque<Line> {
        val grid = Array(rows) { Array(columns) {0.0} }
        val lines: ArrayDeque<Line> = ArrayDeque()

        for(i in 0 until rows)
            for(j in 0 until columns) {
                grid[i][j] = expression.evaluate(mapOf("x" to x1+i*dx, "y" to y1+j*dy))
            }

        for(i in 0 until rows-1)
            for(j in 0 until columns-1) {
                if (differentSign(grid[i][j], grid[i][j+1]))
                {
                    lines.add(Line(Node(x1 + i * dx - 0.5 * dx, y1 + j * dy + 0.5 * dy), Node(x1 + i * dx + 0.5 * dx, y1 + j * dy + 0.5 * dy)))
                }
                if (differentSign(grid[i][j], grid[i+1][j]))
                {
                    lines.add(Line(Node(x1 + i * dx + 0.5 * dx, y1 + j * dy - 0.5 * dy), Node(x1 + i * dx + 0.5 * dx, y1 + j * dy + 0.5 * dy)))
                }
            }

        for(i in 0 until rows)
            if (differentSign(grid[i][columns-1], grid[i][columns-1]))
            {
                lines.add(Line(Node(x1 + i * dx + 0.5 * dx, y2 - 0.5 * dy), Node(x1 + i * dx + 0.5 * dx, y2 + 0.5 * dy)))
            }

        for(j in 0 until columns)
            if (differentSign(grid[rows-1][j], grid[rows-1][j]))
            {
                lines.add(Line(Node(x2 - 0.5 * dx, y1 + j * dy + 0.5 * dy), Node(x2 + 0.5 * dx, y1 + j * dy + 0.5 * dy)))
            }

        for(line in lines) {
            line.first = newtonMethod(line.first, expression, gradient, dx)
            line.second = newtonMethod(line.second, expression, gradient, dx)
        }

        return lines
    }
}