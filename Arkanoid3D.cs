// Arkanoid3D.cs
using System;
using System.Collections.Generic;
using System.Threading;
using System.Linq;

namespace Arkanoid3D
{
    class Program
    {
        static void Main(string[] args)
        {
            Console.OutputEncoding = System.Text.Encoding.UTF8;
            Console.CursorVisible = false;
            var game = new Arkanoid3D();
            game.Run();
        }
    }

    class Arkanoid3D
    {
        private const int WIDTH = 60;
        private const int HEIGHT = 25;
        private const int PADDLE_WIDTH = 8;
        private const int FPS = 30;
        private const int FRAME_TIME = 1000 / FPS;

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
        private bool running;
        private bool gameOver;
        private bool paused;
        private int paddleY;
        private Random random;
        private Dictionary<ConsoleKey, bool> keys;

        class Brick
        {
            public int X, Y, W, H, HP;
            public double Depth;
            public bool Alive;
        }

        class Bonus
        {
            public int X, Y;
            public string Type;
        }

        class ExtraBall
        {
            public double X, Y, Dx, Dy;
        }

        class Particle
        {
            public double X, Y, Dx, Dy;
            public int Life;
        }

        public Arkanoid3D()
        {
            random = new Random();
            keys = new Dictionary<ConsoleKey, bool>();
            ResetGame();
        }

        private void ResetGame()
        {
            score = 0;
            lives = 3;
            level = 1;
            paddleX = WIDTH / 2 - PADDLE_WIDTH / 2;
            paddleWidth = PADDLE_WIDTH;
            ballX = WIDTH / 2;
            ballY = HEIGHT - 3;
            ballDx = (random.NextDouble() > 0.5 ? 1 : -1) * 1.5;
            ballDy = -2.0;
            bricks = CreateBricks();
            bonuses = new List<Bonus>();
            extraBalls = new List<ExtraBall>();
            particles = new List<Particle>();
            running = true;
            gameOver = false;
            paused = false;
            paddleY = HEIGHT - 1;
        }

        private List<Brick> CreateBricks()
        {
            var bricks = new List<Brick>();
            int rows = 3 + level;
            int cols = WIDTH / 4 - 1;
            for (int r = 0; r < rows; r++)
            {
                for (int c = 0; c < cols; c++)
                {
                    double depth = (double)r / rows;
                    int w = 3 + (int)(depth * 2);
                    int hp = r < 2 ? 2 : 1;
                    bricks.Add(new Brick
                    {
                        X = c * 4 + 2,
                        Y = r * 2 + 2,
                        W = w,
                        H = 1,
                        HP = hp,
                        Depth = depth,
                        Alive = true
                    });
                }
            }
            return bricks;
        }

        private void ClearScreen()
        {
            Console.Clear();
        }

        private void Draw()
        {
            char[][] screen = new char[HEIGHT][];
            for (int i = 0; i < HEIGHT; i++)
            {
                screen[i] = new char[WIDTH];
                for (int j = 0; j < WIDTH; j++) screen[i][j] = ' ';
            }

            // Bricks
            foreach (var b in bricks)
            {
                if (!b.Alive) continue;
                char ch = b.HP == 2 ? '▓' : '▒';
                for (int dy = 0; dy < b.H; dy++)
                {
                    for (int dx = 0; dx < b.W; dx++)
                    {
                        int x = b.X + dx, y = b.Y + dy;
                        if (x < WIDTH && y < HEIGHT) screen[y][x] = ch;
                    }
                }
            }

            // Paddle
            for (int i = 0; i < paddleWidth; i++)
            {
                if (paddleX + i < WIDTH) screen[paddleY][paddleX + i] = '█';
            }

            // Ball
            int bx = (int)ballX, by = (int)ballY;
            if (bx < WIDTH && by < HEIGHT) screen[by][bx] = '●';

            // Extra balls
            foreach (var eb in extraBalls)
            {
                int ex = (int)eb.X, ey = (int)eb.Y;
                if (ex < WIDTH && ey < HEIGHT) screen[ey][ex] = '●';
            }

            // Bonuses
            foreach (var b in bonuses)
            {
                if (b.Y < HEIGHT) screen[b.Y][b.X] = '★';
            }

            // Particles
            foreach (var p in particles)
            {
                int px = (int)p.X, py = (int)p.Y;
                if (px >= 0 && px < WIDTH && py >= 0 && py < HEIGHT) screen[py][px] = '·';
            }

            ClearScreen();
            Console.WriteLine(new string('=', WIDTH));
            foreach (var row in screen) Console.WriteLine(new string(row));
            Console.WriteLine(new string('=', WIDTH));
            string ui = $" Score: {score}  Level: {level}  Lives: {lives}  ";
            if (gameOver) ui += " GAME OVER! Press R to restart ";
            Console.WriteLine(ui);
        }

        private void Update()
        {
            if (gameOver || paused) return;

            ballX += ballDx;
            ballY += ballDy;

            // Walls
            if (ballX <= 0 || ballX >= WIDTH - 1) ballDx = -ballDx;
            if (ballY <= 0) ballDy = -ballDy;

            // Paddle
            if (ballY >= HEIGHT - 2 && ballX >= paddleX && ballX < paddleX + paddleWidth)
            {
                ballDy = -Math.Abs(ballDy);
                double offset = (ballX - (paddleX + paddleWidth / 2.0)) / (paddleWidth / 2.0);
                ballDx += offset * 0.5;
                if (ballDx > 2.5) ballDx = 2.5;
                if (ballDx < -2.5) ballDx = -2.5;
            }

            // Bricks
            foreach (var b in bricks)
            {
                if (!b.Alive) continue;
                if (ballX >= b.X && ballX < b.X + b.W && ballY >= b.Y && ballY < b.Y + b.H)
                {
                    b.HP--;
                    if (b.HP <= 0)
                    {
                        b.Alive = false;
                        score += 10;
                        SpawnParticles(b.X + b.W / 2, b.Y + b.H / 2);
                        if (random.NextDouble() < 0.15)
                        {
                            string[] types = { "wide", "slow", "multi" };
                            bonuses.Add(new Bonus { X = b.X + b.W / 2, Y = b.Y, Type = types[random.Next(3)] });
                        }
                    }
                    else
                    {
                        score += 5;
                    }
                    ballDy = -ballDy;
                    break;
                }
            }

            // Ball lost
            if (ballY >= HEIGHT)
            {
                if (extraBalls.Count > 0)
                {
                    var eb = extraBalls[0];
                    extraBalls.RemoveAt(0);
                    ballX = eb.X; ballY = eb.Y; ballDx = eb.Dx; ballDy = eb.Dy;
                }
                else
                {
                    lives--;
                    if (lives <= 0) gameOver = true;
                    else
                    {
                        ballX = WIDTH / 2;
                        ballY = HEIGHT - 3;
                        ballDx = (random.NextDouble() > 0.5 ? 1 : -1) * 1.5;
                        ballDy = -2.0;
                    }
                }
            }

            // Bonuses
            for (int i = bonuses.Count - 1; i >= 0; i--)
            {
                var b = bonuses[i];
                b.Y++;
                if (b.Y >= HEIGHT - 1) { bonuses.RemoveAt(i); continue; }
                if (b.X >= paddleX && b.X < paddleX + paddleWidth && b.Y >= HEIGHT - 2)
                {
                    ApplyBonus(b.Type);
                    bonuses.RemoveAt(i);
                }
            }

            // Particles
            for (int i = particles.Count - 1; i >= 0; i--)
            {
                var p = particles[i];
                p.X += p.Dx; p.Y += p.Dy; p.Life--;
                if (p.Life <= 0) particles.RemoveAt(i);
            }

            // Level complete
            if (!bricks.Any(b => b.Alive))
            {
                level++;
                bricks = CreateBricks();
                ballX = WIDTH / 2;
                ballY = HEIGHT - 3;
                ballDx = (random.NextDouble() > 0.5 ? 1 : -1) * 1.5;
                ballDy = -2.0;
                score += 50 * level;
            }
        }

        private void ApplyBonus(string type)
        {
            switch (type)
            {
                case "wide": paddleWidth = Math.Min(paddleWidth + 4, 16); break;
                case "slow": ballDx *= 0.8; ballDy *= 0.8; break;
                case "multi": extraBalls.Add(new ExtraBall { X = ballX, Y = ballY, Dx = -ballDx, Dy = -ballDy }); break;
            }
        }

        private void SpawnParticles(int x, int y)
        {
            for (int i = 0; i < 10; i++)
            {
                particles.Add(new Particle
                {
                    X = x + (random.NextDouble() - 0.5) * 4,
                    Y = y + (random.NextDouble() - 0.5) * 4,
                    Dx = (random.NextDouble() - 0.5) * 0.5,
                    Dy = (random.NextDouble() - 0.5) * 0.5,
                    Life = random.Next(5, 15)
                });
            }
        }

        public void Run()
        {
            Console.WriteLine("\x1b[36mArkanoid 3D - Use A/D or ←/→ to move, Q to quit, R to restart\x1b[0m");
            Console.WriteLine("Press any key to start...");
            Console.ReadKey(true);

            while (running)
            {
                // Input handling
                while (Console.KeyAvailable)
                {
                    var key = Console.ReadKey(true).Key;
                    if (key == ConsoleKey.Q) { running = false; return; }
                    if ((key == ConsoleKey.R) && gameOver) { ResetGame(); running = true; return; }
                    if (key == ConsoleKey.A || key == ConsoleKey.LeftArrow) paddleX = Math.Max(0, paddleX - 2);
                    if (key == ConsoleKey.D || key == ConsoleKey.RightArrow) paddleX = Math.Min(WIDTH - paddleWidth, paddleX + 2);
                }

                Update();
                Draw();
                Thread.Sleep(FRAME_TIME);
            }
            Console.WriteLine($"\x1b[33mGame Over! Final score: {score}\x1b[0m");
        }
    }
}
