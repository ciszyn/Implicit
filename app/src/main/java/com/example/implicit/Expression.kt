package com.example.implicit
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.math.*

fun String.isDouble(): Boolean = try {
    this.toDouble()
    true
} catch(e: NumberFormatException) {
    false
}

class Expression(expression: String) {
    private var queue: ArrayDeque<Element> = ArrayDeque()

    init {
        val stack: ArrayDeque<Element> = ArrayDeque()
        val equation = expression.replace(" ", "")
        val tokenizer = StringTokenizer(equation, "+-*/^()", true)
        for (token in tokenizer.toList()) {
            val element: Element = create(token as String)
            element.process(queue, stack)
        }
        while(!stack.isEmpty()) {
            queue.add(stack.removeLast())
        }
    }

    fun evaluate(map: Map<String, Double>): Double {
        val stack: ArrayDeque<Double> = ArrayDeque()
        for(element in queue) {
            stack.add(element.evaluate(stack, map))
        }
        return stack.last()
    }

    fun evaluate(): String {
        var result = ""
        for (symbol in queue) {
            result += "$symbol "
        }
        return result
    }

    fun composition(map: Map<String, Expression>): Expression {
        val result = Expression("")
        result.queue.addAll(queue)

        result.queue.forEachIndexed{ i: Int, x: Element ->
            if(map.containsKey(x.symbol)) {
                result.queue.removeAt(i)
                result.queue.addAll(i, map[x.symbol]?.queue ?: Expression(x.symbol).queue)
            }
        }
        return result
    }

    fun derivative(variable: String): Expression {
        val f: ArrayDeque<Expression> = ArrayDeque()
        val fprim: ArrayDeque<Expression> = ArrayDeque()

        for (element in queue)
            element.derivative(f, fprim, variable)

        return fprim.last()
    }

    companion object ElementFactory {
        fun create(x: String): Element = when {
            x == "(" -> LeftParenthesis(x)
            x == ")" -> RightParenthesis(x)
            x.isDouble() -> Numerical(x)
            x.length == 1 && x[0].isLetter() -> Variable(x)
            else -> Operation(x)
        }
    }

    abstract class Element(val symbol: String) {
        var precedence: Int = 0
        var associativity: Boolean = false
        override fun toString(): String = symbol
        abstract fun process(queue: ArrayDeque<Element>, stack: ArrayDeque<Element>)
        abstract fun evaluate(stack: ArrayDeque<Double>, map: Map<String, Double>): Double
        open fun composition(map: Map<String, Double>): Element = this
        abstract fun derivative(f: ArrayDeque<Expression>, fprim: ArrayDeque<Expression>, variable: String)
    }

    class Numerical(x: String) : Element(x) {
        private var number: Double = 0.0
        init {
            number = x.toDouble()
        }

        constructor(x: Double) : this(x.toString()) {
            number = x
        }

        override fun process(queue: ArrayDeque<Element>, stack: ArrayDeque<Element>) {
            queue.add(this)
        }

        override fun evaluate(stack: ArrayDeque<Double>, map: Map<String, Double>): Double {
            return number
        }

        override fun derivative(f: ArrayDeque<Expression>, fprim: ArrayDeque<Expression>, variable: String) {
            f.add(Expression(symbol))
            fprim.add(Expression("0"))
        }
    }

    class Operation(x: String) : Element(x) {
        var function: (ArrayDeque<Double>) -> Double = {_ -> 0.0}
        var derivative: (ArrayDeque<Expression>, ArrayDeque<Expression>) -> Unit

        init {
            when(x) {
                "+" -> {
                    precedence = 2
                    function = { x -> x.removeLast() + x.removeLast() }
                    derivative = { f, fprim ->
                        val f2 = f.removeLast()
                        val f1 = f.removeLast()
                        val f2prim = fprim.removeLast()
                        val f1prim = fprim.removeLast()

                        val der = Expression("b+d").composition(mapOf("a" to f1, "b" to f1prim, "c" to f2, "d" to f2prim))
                        val expr = Expression("a+c").composition(mapOf("a" to f1, "b" to f1prim, "c" to f2, "d" to f2prim))

                        f.add(expr)
                        fprim.add(der)
                    }
                }
                "-" -> {
                    precedence = 2
                    function = { x -> -x.removeLast() + x.removeLast() }
                    derivative = { f, fprim ->
                        val f2 = f.removeLast()
                        val f1 = f.removeLast()
                        val f2prim = fprim.removeLast()
                        val f1prim = fprim.removeLast()

                        val der = Expression("b-d").composition(mapOf("a" to f1, "b" to f1prim, "c" to f2, "d" to f2prim))
                        val expr = Expression("a-c").composition(mapOf("a" to f1, "b" to f1prim, "c" to f2, "d" to f2prim))

                        f.add(expr)
                        fprim.add(der)
                    }
                }
                "*" -> {
                    precedence = 3
                    //hack around 0 * NaN
                    function = fun (x: ArrayDeque<Double>): Double {
                        val a = x.removeLast()
                        val b = x.removeLast()
                        return if (a == 0.0 || b == 0.0) 0.0 else a * b
                    }
                    derivative = { f, fprim ->
                        val f2 = f.removeLast()
                        val f1 = f.removeLast()
                        val f2prim = fprim.removeLast()
                        val f1prim = fprim.removeLast()

                        val der = Expression("b*c+a*d").composition(mapOf("a" to f1, "b" to f1prim, "c" to f2, "d" to f2prim))
                        val expr = Expression("a*c").composition(mapOf("a" to f1, "b" to f1prim, "c" to f2, "d" to f2prim))

                        f.add(expr)
                        fprim.add(der)
                    }
                }
                "/" -> {
                    precedence = 3
                    function = { x -> div(b=x.removeLast(), a=x.removeLast()) }
                    derivative = { f, fprim ->
                        val f2 = f.removeLast()
                        val f1 = f.removeLast()
                        val f2prim = fprim.removeLast()
                        val f1prim = fprim.removeLast()

                        val der = Expression("(b*c-a*d)/(c*c)").composition(mapOf("a" to f1, "b" to f1prim, "c" to f2, "d" to f2prim))
                        val expr = Expression("a/c").composition(mapOf("a" to f1, "b" to f1prim, "c" to f2, "d" to f2prim))

                        f.add(expr)
                        fprim.add(der)
                    }
                }
                "^" -> {
                    precedence = 4
                    function = { x -> pow(b=x.removeLast(), a=x.removeLast()) }
                    derivative =  { f, fprim ->
                        val f2 = f.removeLast()
                        val f1 = f.removeLast()
                        val f2prim = fprim.removeLast()
                        val f1prim = fprim.removeLast()

                        val der = Expression("b*c*a^(c-1)+d*a^c*ln(a)").composition(mapOf("a" to f1, "b" to f1prim, "c" to f2, "d" to f2prim))
                        val expr = Expression("a^c").composition(mapOf("a" to f1, "b" to f1prim, "c" to f2, "d" to f2prim))

                        f.add(expr)
                        fprim.add(der)
                    }
                }
                "sin" -> {
                    precedence = Int.MAX_VALUE
                    function = { x -> sin(x.removeLast())}
                    derivative = { f, fprim ->
                        val f1 = f.removeLast()
                        val f1prim = fprim.removeLast()

                        val der = Expression("b*cos(a)").composition(mapOf("a" to f1, "b" to f1prim))
                        val expr = Expression("sin(a)").composition(mapOf("a" to f1, "b" to f1prim))

                        f.add(expr)
                        fprim.add(der)
                    }
                }
                "cos" -> {
                    precedence = Int.MAX_VALUE
                    function = { x -> cos(x.removeLast()) }
                    derivative = { f, fprim ->
                        val f1 = f.removeLast()
                        val f1prim = fprim.removeLast()

                        val der = Expression("0-b*sin(a)").composition(mapOf("a" to f1, "b" to f1prim))
                        val expr = Expression("cos(a)").composition(mapOf("a" to f1, "b" to f1prim))

                        f.add(expr)
                        fprim.add(der)
                    }
                }
                "tan" -> {
                    precedence = Int.MAX_VALUE
                    function = { x -> tan(x.removeLast()) }
                    derivative = { f, fprim ->
                        val f1 = f.removeLast()
                        val f1prim = fprim.removeLast()

                        val der = Expression("b/(cos(a)*cos(a))").composition(mapOf("a" to f1, "b" to f1prim))
                        val expr = Expression("tan(a)").composition(mapOf("a" to f1, "b" to f1prim))

                        f.add(expr)
                        fprim.add(der)
                    }
                }
                "ln" -> {
                    precedence = Int.MAX_VALUE
                    function = {x -> ln(x.removeLast())}
                    derivative = { f, fprim ->
                        val f1 = f.removeLast()
                        val f1prim = fprim.removeLast()

                        val der = Expression("b/a").composition(mapOf("a" to f1, "b" to f1prim))
                        val expr = Expression("ln(a)").composition(mapOf("a" to f1, "b" to f1prim))

                        f.add(expr)
                        fprim.add(der)
                    }
                }
                "arcsin" -> {
                    precedence = Int.MAX_VALUE
                    function = { x -> asin(x.removeLast()) }
                    derivative = { f, fprim ->
                        val f1 = f.removeLast()
                        val f1prim = fprim.removeLast()

                        val der = Expression("b/sqrt(1-a*a)").composition(mapOf("a" to f1, "b" to f1prim))
                        val expr = Expression("arcsin(a)").composition(mapOf("a" to f1, "b" to f1prim))

                        f.add(expr)
                        fprim.add(der)
                    }
                }
                "arccos" -> {
                    precedence = Int.MAX_VALUE
                    function = { x -> acos(x.removeLast()) }
                    derivative = { f, fprim ->
                        val f1 = f.removeLast()
                        val f1prim = fprim.removeLast()

                        val der = Expression("0-b/sqrt(1-a*a)").composition(mapOf("a" to f1, "b" to f1prim))
                        val expr = Expression("arccos(a)").composition(mapOf("a" to f1, "b" to f1prim))

                        f.add(expr)
                        fprim.add(der)
                    }
                }
                "arctan" -> {
                    precedence = Int.MAX_VALUE
                    function = { x-> atan(x.removeLast()) }
                    derivative = { f, fprim ->
                        val f1 = f.removeLast()
                        val f1prim = fprim.removeLast()

                        val der = Expression("b/(1+a*a)").composition(mapOf("a" to f1, "b" to f1prim))
                        val expr = Expression("arctan(a)").composition(mapOf("a" to f1, "b" to f1prim))

                        f.add(expr)
                        fprim.add(der)
                    }
                }
                "exp" -> {
                    precedence = Int.MAX_VALUE
                    function = { x -> exp(x.removeLast()) }
                    derivative = { f, fprim ->
                        val f1 = f.removeLast()
                        val f1prim = fprim.removeLast()

                        val der = Expression("b*exp(a)").composition(mapOf("a" to f1, "b" to f1prim))
                        val expr = Expression("exp(a)").composition(mapOf("a" to f1, "b" to f1prim))

                        f.add(expr)
                        fprim.add(der)
                    }
                }
                "sqrt" -> {
                    precedence = Int.MAX_VALUE
                    function = { x -> sqrt(x.removeLast()) }
                    derivative = { f, fprim ->
                        val f1 = f.removeLast()
                        val f1prim = fprim.removeLast()

                        val der = Expression("b/(2*sqrt(a))").composition(mapOf("a" to f1, "b" to f1prim))
                        val expr = Expression("sqrt(a)").composition(mapOf("a" to f1, "b" to f1prim))

                        f.add(expr)
                        fprim.add(der)
                    }
                }
                else -> throw Exception("Unknown function $x")
            }

            associativity = (x == "^")
        }
        override fun process(queue: ArrayDeque<Element>, stack: ArrayDeque<Element>) {
            while (!stack.isEmpty() &&
                (stack.last().precedence > precedence ||
                        (stack.last().precedence == precedence && associativity))) {
                queue.add(stack.removeLast())
            }
            stack.add(this)
        }
        private fun div(a: Double, b: Double) = a / b
        private fun pow(a: Double, b: Double) = a.pow(b)

        override fun evaluate(stack: ArrayDeque<Double>, map: Map<String, Double>): Double = function(stack)
        override fun derivative(f: ArrayDeque<Expression>, fprim: ArrayDeque<Expression>, variable: String) = derivative(f, fprim)
    }

    class Variable(x: String) : Element(x) {
        override fun process(queue: ArrayDeque<Element>, stack: ArrayDeque<Element>) {
            queue.add(this)
        }

        override fun evaluate(stack: ArrayDeque<Double>, map: Map<String, Double>): Double {
            return map[symbol] ?: throw Exception("Unknown variable $symbol")
        }

        override fun composition(map: Map<String, Double>): Numerical {
            return Numerical(map[symbol] ?: 0.0)
        }

        override fun derivative(f: ArrayDeque<Expression>, fprim: ArrayDeque<Expression>, variable: String) {
            f.add(Expression(symbol))
            if(symbol == variable)
                fprim.add(Expression("1"))
            else
                fprim.add(Expression("0"))
        }
    }

    class LeftParenthesis(x: String) : Element(x) {
        override fun process(queue: ArrayDeque<Element>, stack: ArrayDeque<Element>) {
            stack.add(this)
        }

        override fun evaluate(stack: ArrayDeque<Double>, map: Map<String, Double>): Double {
            throw Exception("Unmatched left parenthesis")
        }

        override fun derivative(f: ArrayDeque<Expression>, fprim: ArrayDeque<Expression>, variable: String) {
            throw Exception("Unmatched left parenthesis")
        }
    }

    class RightParenthesis(x: String) : Element(x) {
        override fun process(queue: ArrayDeque<Element>, stack: ArrayDeque<Element>) {
            while(!stack.isEmpty() && stack.last().symbol != "(") {
                queue.add(stack.removeLast())
            }
            if(stack.isEmpty() || stack.last().symbol != "(")
                throw Exception("Unmatched right parenthesis")
            stack.removeLast()
        }

        override fun evaluate(stack: ArrayDeque<Double>, map: Map<String, Double>): Double {
            throw Exception("Unmatched right parenthesis")
        }

        override fun derivative(f: ArrayDeque<Expression>, fprim: ArrayDeque<Expression>, variable: String) {
            throw Exception("Unmatched right parenthesis")
        }
    }
}