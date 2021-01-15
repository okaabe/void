package me.otofune.void.interpreter.runtime

import me.otofune.void.grammar.Expr
import me.otofune.void.grammar.Stmt
import me.otofune.void.grammar.TokenType
import me.otofune.void.interpreter.exceptions.VoidRuntimeException

import me.otofune.void.interpreter.runtime.util.checkNumberOperand
import me.otofune.void.interpreter.runtime.util.isEqual
import me.otofune.void.interpreter.runtime.util.isTruthy

class Evaluator(
    private var environment: Environment = Environment()
): Expr.Visitor<Any?>, Stmt.Visitor<Any?> {
    override fun visitExprStmt(stmt: Stmt.ExprStmt): Any? {
        return visitExpr(stmt.expr)
    }

    override fun visitVarStmt(stmt: Stmt.VarStmt) {
        environment.declare(stmt.name.lexeme, visitExpr(stmt.value))
    }

    override fun visitIfStmt(stmt: Stmt.IfStmt) {
        if (isTruthy(visitExpr(stmt.condition))) {
            visitStmt(stmt.thenDo)
        } else stmt.elseDo?.also { visitStmt(it) }
    }

    override fun visitBlockStmt(stmt: Stmt.BlockStmt) = executeBlock(stmt.statements, Environment())

    fun executeBlock(statements: List<Stmt>, scopeEnvironment: Environment) {
        val globalScope = environment.also {
            environment = scopeEnvironment
        }

        try {
            for(statement in statements) visitStmt(statement)
        } finally {
            environment = globalScope
        }
    }

    override fun visitFunctionStmt(stmt: Stmt.FunctionStmt) {
        environment.declare(stmt.name.lexeme, VoidCallable.VoidFunction(stmt, environment))
    }

    override fun visitBinaryExpr(expr: Expr.Binary): Any? {
        val left = visitExpr(expr.left)
        val right = visitExpr(expr.right)

        return when(expr.op.type) {
            TokenType.MINUS -> checkNumberOperand(expr.op, left) - checkNumberOperand(expr.op, right)
            TokenType.STAR -> checkNumberOperand(expr.op, left) * checkNumberOperand(expr.op, right)
            TokenType.SLASH -> checkNumberOperand(expr.op, left) / checkNumberOperand(expr.op, right)
            TokenType.PLUS -> when {
                left is String && right is String -> left + right
                else -> checkNumberOperand(expr.op, left) + checkNumberOperand(expr.op, right)
            }
            TokenType.LESS -> checkNumberOperand(expr.op, left) < checkNumberOperand(expr.op, right)
            TokenType.GREATER -> checkNumberOperand(expr.op, left) > checkNumberOperand(expr.op, right)
            TokenType.LESS_EQUAL -> checkNumberOperand(expr.op, left) <= checkNumberOperand(expr.op, right)
            TokenType.GREATER_EQUAL -> checkNumberOperand(expr.op, left) >= checkNumberOperand(expr.op, right)

            TokenType.EQUAL_EQUAL -> isEqual(left, right)
            TokenType.BANG_EQUAL -> !isEqual(left, right)

            else -> null
        }
    }

    override fun visitGroupingExpr(expr: Expr.Grouping): Any? = visitExpr(expr.expr)

    override fun visitUnaryExpr(expr: Expr.Unary): Any? {
        val right = visitExpr(expr.right)

        return when(expr.op.type) {
            TokenType.MINUS -> -checkNumberOperand(expr.op, right)
            TokenType.BANG -> !isTruthy(right)
            else -> null
        }
    }

    override fun visitLiteralExpr(expr: Expr.Literal): Any? = expr.value

    override fun visitVariableExpr(expr: Expr.Variable): Any? = environment.get(expr.variable.lexeme)

    override fun visitAssignExpr(expr: Expr.Assign): Any? = visitExpr(expr.value).also { value ->
        environment.assign(expr.target.variable.lexeme, value)
    }

    override fun visitCallExpr(expr: Expr.Call): Any? {
        val calle = visitExpr(expr.calle)
        val arguments = mutableListOf<Any?>()

        for (argument in expr.arguments) {
            arguments.add(visitExpr(argument))
        }

        if (calle !is VoidCallable) {
            throw VoidRuntimeException.InvalidCalle(expr.calle)
        }

        if (arguments.size != calle.arity()) {
            throw VoidRuntimeException.InvalidArgumentsAmount(calle.arity(), arguments.size)
        }

        return calle.call(this, arguments)
    }


}