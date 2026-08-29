// Arkanoid3D.java
import java.util.*;
import java.io.*;
import java.awt.event.KeyEvent;
import java.lang.Thread;

public class Arkanoid3D {
    private static final int WIDTH = 60;
    private static final int HEIGHT = 25;
    private static final int PADDLE_WIDTH = 8;
    private static final int FPS = 30;
    private static final long FRAME_TIME = 1000 / FPS;

    private int score;
    private int lives;
    private int level;
    private int paddleX;
    private int paddleWidth;
    private double ballX;
    private double ballY;
    private double ballDx;
    private double ballDy;
    private List<Brick> bricks;
    private List<Bonus> bonuses;
    private List<ExtraBall> extraBalls;
    private List<Particle> particles;
    private boolean running;
    private boolean gameOver;
    private boolean paused;
    private int paddleY;
    private Random random;
    private Set<Integer> pressedKeys;

    static class Brick {
        int x, y, w, h, hp;
        double depth;
        boolean alive;
        Brick(int x, int y, int w, int h, int hp, double depth) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.hp = hp; this.depth = depth; this.alive = true;
        }
    }

    static class Bonus {
        int x, y;
        String type;
        Bonus(int x, int y, String type) { this.x = x; this.y = y; this.type = type; }
    }

    static class ExtraBall {
        double x, y, dx, dy;
        ExtraBall(double x, double y, double dx, double dy) { this.x = x; this.y = y; this.dx = dx; this.dy = dy; }
    }

    static class Particle {
        double x, y, dx, dy;
        int life;
        Particle(double x, double y, double dx, double dy, int life) {
            this.x = x; this.y = y; this.dx = dx; this.dy = dy; this.life = life;
        }
    }

    public Arkanoid3D() {
        random = new Random();
        pressedKeys = new HashSet<>();
        resetGame();
    }

    private void resetGame() {
        score = 0;
        lives = 3;
        level = 1;
        paddleX = WIDTH / 2 - PADDLE_WIDTH / 2;
        paddleWidth = PADDLE_WIDTH;
        ballX = WIDTH / 2;
        ballY = HEIGHT - 3;
        ballDx = (random.nextBoolean() ? 1 : -1) * 1.5;
        ballDy = -2.0;
        bricks = createBricks();
        bonuses = new ArrayList<>();
        extraBalls = new ArrayList<>();
        particles = new ArrayList<>();
        running = true;
        gameOver = false;
        paused = false;
        paddleY = HEIGHT - 1;
    }

    private List<Brick> createBricks() {
        List<Brick> bricks = new ArrayList<>();
        int rows = 3 + level;
        int cols = WIDTH / 4 - 1;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double depth = (double) r / rows;
                int w = 3 + (int)(depth * 2);
                int hp = r < 2 ? 2 : 1;
                bricks.add(new Brick(c * 4 + 2, r * 2 + 2, w, 1, hp, depth));
            }
        }
        return bricks;
    }

    private void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    private void draw() {
        char[][] screen = new char[HEIGHT][WIDTH];
        for (int i = 0; i < HEIGHT; i++) Arrays.fill(screen[i], ' ');

        // Bricks
        for (Brick b : bricks) {
            if (!b.alive) continue;
            String color = "\033[97m";
            if (b.depth < 0.3) color = "\033[97m";
            else if (b.depth < 0.6) color = "\033[36m";
            else color = "\033[34m";
            if (b.hp == 2) color = "\033[33m";
            char ch = b.hp == 2 ? '▓' : '▒';
            for (int dy = 0; dy < b.h; dy++) {
                for (int dx = 0; dx < b.w; dx++) {
                    int x = b.x + dx, y = b.y + dy;
                    if (x < WIDTH && y < HEIGHT) screen[y][x] = ch;
                }
            }
        }

        // Paddle
        for (int i = 0; i < paddleWidth; i++) {
            if (paddleX + i < WIDTH) screen[paddleY][paddleX + i] = '█';
        }

        // Ball
        int bx = (int)ballX, by = (int)ballY;
        if (bx < WIDTH && by < HEIGHT) screen[by][bx] = '●';

        // Extra balls
        for (ExtraBall eb : extraBalls) {
            int ex = (int)eb.x, ey = (int)eb.y;
            if (ex < WIDTH && ey < HEIGHT) screen[ey][ex] = '●';
        }

        // Bonuses
        for (Bonus b : bonuses) {
            if (b.y < HEIGHT) screen[b.y][b.x] = '★';
        }

        // Particles
        for (Particle p : particles) {
            int px = (int)p.x, py = (int)p.y;
            if (px >= 0 && px < WIDTH && py >= 0 && py < HEIGHT) screen[py][px] = '·';
        }

        clearScreen();
        System.out.println("=".repeat(WIDTH));
        for (char[] row : screen) System.out.println(new String(row));
        System.out.println("=".repeat(WIDTH));
        String ui = String.format(" Score: %d  Level: %d  Lives: %d  ", score, level, lives);
        if (gameOver) ui += " GAME OVER! Press R to restart ";
        System.out.println(ui);
    }

    private void update() {
        if (gameOver || paused) return;

        ballX += ballDx;
        ballY += ballDy;

        // Walls
        if (ballX <= 0 || ballX >= WIDTH - 1) ballDx = -ballDx;
        if (ballY <= 0) ballDy = -ballDy;

        // Paddle
        if (ballY >= HEIGHT - 2 && ballX >= paddleX && ballX < paddleX + paddleWidth) {
            ballDy = -Math.abs(ballDy);
            double offset = (ballX - (paddleX + paddleWidth / 2.0)) / (paddleWidth / 2.0);
            ballDx += offset * 0.5;
            if (ballDx > 2.5) ballDx = 2.5;
            if (ballDx < -2.5) ballDx = -2.5;
        }

        // Bricks
        for (Brick b : bricks) {
            if (!b.alive) continue;
            if (ballX >= b.x && ballX < b.x + b.w && ballY >= b.y && ballY < b.y + b.h) {
                b.hp--;
                if (b.hp <= 0) {
                    b.alive = false;
                    score += 10;
                    spawnParticles(b.x + b.w / 2, b.y + b.h / 2);
                    if (random.nextDouble() < 0.15) {
                        String[] types = {"wide", "slow", "multi"};
                        bonuses.add(new Bonus(b.x + b.w / 2, b.y, types[random.nextInt(3)]));
                    }
                } else {
                    score += 5;
                }
                ballDy = -ballDy;
                break;
            }
        }

        // Ball lost
        if (ballY >= HEIGHT) {
            if (!extraBalls.isEmpty()) {
                ExtraBall eb = extraBalls.remove(0);
                ballX = eb.x; ballY = eb.y; ballDx = eb.dx; ballDy = eb.dy;
            } else {
                lives--;
                if (lives <= 0) gameOver = true;
                else {
                    ballX = WIDTH / 2;
                    ballY = HEIGHT - 3;
                    ballDx = (random.nextBoolean() ? 1 : -1) * 1.5;
                    ballDy = -2.0;
                }
            }
        }

        // Bonuses
        for (int i = bonuses.size() - 1; i >= 0; i--) {
            Bonus b = bonuses.get(i);
            b.y++;
            if (b.y >= HEIGHT - 1) { bonuses.remove(i); continue; }
            if (b.x >= paddleX && b.x < paddleX + paddleWidth && b.y >= HEIGHT - 2) {
                applyBonus(b.type);
                bonuses.remove(i);
            }
        }

        // Particles
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.x += p.dx; p.y += p.dy; p.life--;
            if (p.life <= 0) particles.remove(i);
        }

        // Level complete
        boolean allDead = true;
        for (Brick b : bricks) if (b.alive) { allDead = false; break; }
        if (allDead) {
            level++;
            bricks = createBricks();
            ballX = WIDTH / 2;
            ballY = HEIGHT - 3;
            ballDx = (random.nextBoolean() ? 1 : -1) * 1.5;
            ballDy = -2.0;
            score += 50 * level;
        }
    }

    private void applyBonus(String type) {
        switch (type) {
            case "wide": paddleWidth = Math.min(paddleWidth + 4, 16); break;
            case "slow": ballDx *= 0.8; ballDy *= 0.8; break;
            case "multi": extraBalls.add(new ExtraBall(ballX, ballY, -ballDx, -ballDy)); break;
        }
    }

    private void spawnParticles(int x, int y) {
        for (int i = 0; i < 10; i++) {
            particles.add(new Particle(
                x + (random.nextDouble() - 0.5) * 4,
                y + (random.nextDouble() - 0.5) * 4,
                (random.nextDouble() - 0.5) * 0.5,
                (random.nextDouble() - 0.5) * 0.5,
                random.nextInt(10) + 5
            ));
        }
    }

    private void handleInput() throws IOException {
        // В Java сложно сделать неблокирующий ввод, используем простой вариант
        // В реальном коде нужно использовать библиотеку для работы с консолью
        try {
            if (System.in.available() > 0) {
                char ch = (char) System.in.read();
                if (ch == 'q' || ch == 'Q') { running = false; return; }
                if ((ch == 'r' || ch == 'R') && gameOver) { resetGame(); running = true; return; }
                if (ch == 'a' || ch == 'A') paddleX = Math.max(0, paddleX - 2);
                if (ch == 'd' || ch == 'D') paddleX = Math.min(WIDTH - paddleWidth, paddleX + 2);
            }
        } catch (IOException e) {}
    }

    public void run() throws InterruptedException, IOException {
        System.out.println("\033[36mArkanoid 3D - Use A/D to move, Q to quit, R to restart\033[0m");
        System.out.println("Press any key to start...");
        System.in.read();

        while (running) {
            handleInput();
            update();
            draw();
            Thread.sleep(FRAME_TIME);
        }
        System.out.println("\033[33mGame Over! Final score: " + score + "\033[0m");
    }

    public static void main(String[] args) throws Exception {
        Arkanoid3D game = new Arkanoid3D();
        game.run();
    }
}
