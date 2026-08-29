// Arkanoid3D.kt
import kotlin.system.exitProcess
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.*

class Arkanoid3D {
    companion object {
        const val WIDTH = 60
        const val HEIGHT = 25
        const val PADDLE_WIDTH = 8
        const val FPS = 30
        const val FRAME_TIME = 1000 / FPS
    }

    data class Brick(var x: Int, var y: Int, var w: Int, var h: Int, var hp: Int, var depth: Double, var alive: Boolean)
    data class Bonus(var x: Int, var y: Int, var type: String)
    data class Particle(var x: Double, var y: Double, var dx: Double, var dy: Double, var life: Int)

    private var score = 0
    private var lives = 3
    private var level = 1
    private var paddleX = WIDTH / 2 - PADDLE_WIDTH / 2
    private var paddleWidth = PADDLE_WIDTH
    private var ballX = WIDTH / 2.0
    private var ballY = (HEIGHT - 3).toDouble()
    private var ballDx = (if (Random.nextBoolean()) 1.0 else -1.0) * 1.5
    private var ballDy = -2.0
    private val bricks = mutableListOf<Brick>()
    private val bonuses = mutableListOf<Bonus>()
    private val extraBalls = mutableListOf<Triple<Double, Double, Double>>()
    private val particles = mutableListOf<Particle>()
    private var running = true
    private var gameOver = false
    private var paused = false
    private val paddleY = HEIGHT - 1
    private val random = Random(System.currentTimeMillis())
    private val keyChannel = Channel<Char>(capacity = 10)

    init {
        createBricks()
    }

    private fun createBricks() {
        bricks.clear()
        val rows = 3 + level
        val cols = WIDTH / 4 - 1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val depth = r.toDouble() / rows
                val w = 3 + (depth * 2).toInt()
                val hp = if (r < 2) 2 else 1
                bricks.add(Brick(c * 4 + 2, r * 2 + 2, w, 1, hp, depth, true))
            }
        }
    }

    private fun clearScreen() {
        print("\u001B[2J\u001B[1;1H")
    }

    private fun draw() {
        val screen = Array(HEIGHT) { CharArray(WIDTH) { ' ' } }

        for (b in bricks) {
            if (!b.alive) continue
            val ch = if (b.hp == 2) '▓' else '▒'
            for (dy in 0 until b.h) {
                for (dx in 0 until b.w) {
                    val x = b.x + dx
                    val y = b.y + dy
                    if (x < WIDTH && y < HEIGHT) screen[y][x] = ch
                }
            }
        }

        for (i in 0 until paddleWidth) {
            if (paddleX + i < WIDTH) screen[paddleY][paddleX + i] = '█'
        }

        val bx = ballX.toInt()
        val by = ballY.toInt()
        if (bx < WIDTH && by < HEIGHT) screen[by][bx] = '●'

        for (eb in extraBalls) {
            val ex = eb.first.toInt()
            val ey = eb.second.toInt()
            if (ex < WIDTH && ey < HEIGHT) screen[ey][ex] = '●'
        }

        for (b in bonuses) {
            if (b.y < HEIGHT) screen[b.y][b.x] = '★'
        }

        for (p in particles) {
            val px = p.x.toInt()
            val py = p.y.toInt()
            if (px in 0 until WIDTH && py in 0 until HEIGHT) screen[py][px] = '·'
        }

        clearScreen()
        println("=".repeat(WIDTH))
        for (row in screen) println(row.joinToString(""))
        println("=".repeat(WIDTH))
        var ui = " Score: $score  Level: $level  Lives: $lives  "
        if (gameOver) ui += " GAME OVER! Press R to restart "
        println(ui)
    }

    private fun spawnParticles(x: Int, y: Int) {
        for (i in 0 until 10) {
            particles.add(Particle(
                x + (random.nextDouble() - 0.5) * 4,
                y + (random.nextDouble() - 0.5) * 4,
                (random.nextDouble() - 0.5) * 0.5,
                (random.nextDouble() - 0.5) * 0.5,
                random.nextInt(10) + 5
            ))
        }
    }

    private fun applyBonus(type: String) {
        when (type) {
            "wide" -> paddleWidth = minOf(paddleWidth + 4, 16)
            "slow" -> { ballDx *= 0.8; ballDy *= 0.8 }
            "multi" -> extraBalls.add(Triple(ballX, ballY, ballDy))
        }
    }

    private fun update() {
        if (gameOver || paused) return

        ballX += ballDx
        ballY += ballDy

        if (ballX <= 0 || ballX >= WIDTH - 1) ballDx = -ballDx
        if (ballY <= 0) ballDy = -ballDy

        if (ballY >= HEIGHT - 2 && ballX >= paddleX && ballX < paddleX + paddleWidth) {
            ballDy = -kotlin.math.abs(ballDy)
            val offset = (ballX - (paddleX + paddleWidth / 2.0)) / (paddleWidth / 2.0)
            ballDx += offset * 0.5
            ballDx = ballDx.coerceIn(-2.5, 2.5)
        }

        for (b in bricks) {
            if (!b.alive) continue
            if (ballX >= b.x && ballX < b.x + b.w && ballY >= b.y && ballY < b.y + b.h) {
                b.hp--
                if (b.hp <= 0) {
                    b.alive = false
                    score += 10
                    spawnParticles(b.x + b.w / 2, b.y + b.h / 2)
                    if (random.nextDouble() < 0.15) {
                        val types = listOf("wide", "slow", "multi")
                        bonuses.add(Bonus(b.x + b.w / 2, b.y, types[random.nextInt(3)]))
                    }
                } else {
                    score += 5
                }
                ballDy = -ballDy
                break
            }
        }

        if (ballY >= HEIGHT) {
            if (extraBalls.isNotEmpty()) {
                val eb = extraBalls.removeAt(0)
                ballX = eb.first
                ballY = eb.second
            } else {
                lives--
                if (lives <= 0) gameOver = true
                else {
                    ballX = WIDTH / 2.0
                    ballY = (HEIGHT - 3).toDouble()
                    ballDx = (if (random.nextBoolean()) 1.0 else -1.0) * 1.5
                    ballDy = -2.0
                }
            }
        }

        for (i in bonuses.indices.reversed()) {
            val b = bonuses[i]
            b.y++
            if (b.y >= HEIGHT - 1) { bonuses.removeAt(i); continue }
            if (b.x >= paddleX && b.x < paddleX + paddleWidth && b.y >= HEIGHT - 2) {
                applyBonus(b.type)
                bonuses.removeAt(i)
            }
        }

        for (i in particles.indices.reversed()) {
            val p = particles[i]
            p.x += p.dx
            p.y += p.dy
            p.life--
            if (p.life <= 0) particles.removeAt(i)
        }

        if (bricks.all { !it.alive }) {
            level++
            createBricks()
            ballX = WIDTH / 2.0
            ballY = (HEIGHT - 3).toDouble()
            ballDx = (if (random.nextBoolean()) 1.0 else -1.0) * 1.5
            ballDy = -2.0
            score += 50 * level
        }
    }

    suspend fun run() = coroutineScope {
        println("\u001B[36mArkanoid 3D - Use A/D to move, Q to quit, R to restart\u001B[0m")
        println("Press any key to start...")
        System.`in`.read()

        launch {
            val reader = System.`in`.bufferedReader()
            while (running) {
                if (reader.ready()) {
                    val ch = reader.read()
                    keyChannel.send(ch.toChar())
                }
                delay(10)
            }
        }

        while (running) {
            while (!keyChannel.isEmpty) {
                val ch = keyChannel.receive()
                when (ch) {
                    'q', 'Q' -> { running = false; return@coroutineScope }
                    'r', 'R' -> if (gameOver) { resetGame(); running = true }
                    'a', 'A' -> paddleX = maxOf(0, paddleX - 2)
                    'd', 'D' -> paddleX = minOf(WIDTH - paddleWidth, paddleX + 2)
                }
            }

            update()
            draw()
            delay(FRAME_TIME.toLong())
        }
        println("\u001B[33mGame Over! Final score: $score\u001B[0m")
    }

    private fun resetGame() {
        score = 0
        lives = 3
        level = 1
        paddleX = WIDTH / 2 - PADDLE_WIDTH / 2
        paddleWidth = PADDLE_WIDTH
        ballX = WIDTH / 2.0
        ballY = (HEIGHT - 3).toDouble()
        ballDx = (if (random.nextBoolean()) 1.0 else -1.0) * 1.5
        ballDy = -2.0
        createBricks()
        bonuses.clear()
        extraBalls.clear()
        particles.clear()
        gameOver = false
        running = true
    }
}

fun main(args: Array<String>) = runBlocking {
    val game = Arkanoid3D()
    game.run()
}
