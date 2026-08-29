// arkanoid3d.cpp
#include <iostream>
#include <vector>
#include <cmath>
#include <cstdlib>
#include <ctime>
#include <thread>
#include <chrono>
#include <termios.h>
#include <unistd.h>
#include <fcntl.h>

using namespace std;

const int WIDTH = 60;
const int HEIGHT = 25;
const int PADDLE_WIDTH = 8;
const int FPS = 30;
const int FRAME_TIME = 1000 / FPS;

struct Brick {
    int x, y, w, h, hp;
    double depth;
    bool alive;
};

struct Bonus {
    int x, y;
    string type;
};

struct Particle {
    double x, y, dx, dy;
    int life;
};

class Arkanoid3D {
private:
    int score, lives, level;
    int paddleX, paddleWidth;
    double ballX, ballY, ballDx, ballDy;
    vector<Brick> bricks;
    vector<Bonus> bonuses;
    vector<pair<double, double>> extraBalls;
    vector<Particle> particles;
    bool running, gameOver, paused;
    int paddleY;

    vector<Brick> createBricks() {
        vector<Brick> bricks;
        int rows = 3 + level;
        int cols = WIDTH / 4 - 1;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double depth = (double)r / rows;
                int w = 3 + (int)(depth * 2);
                int hp = r < 2 ? 2 : 1;
                bricks.push_back({c * 4 + 2, r * 2 + 2, w, 1, hp, depth, true});
            }
        }
        return bricks;
    }

    void clearScreen() {
        cout << "\033[2J\033[1;1H";
    }

    void draw() {
        vector<vector<char>> screen(HEIGHT, vector<char>(WIDTH, ' '));

        for (auto& b : bricks) {
            if (!b.alive) continue;
            char ch = b.hp == 2 ? '▓' : '▒';
            for (int dy = 0; dy < b.h; dy++) {
                for (int dx = 0; dx < b.w; dx++) {
                    int x = b.x + dx, y = b.y + dy;
                    if (x < WIDTH && y < HEIGHT) screen[y][x] = ch;
                }
            }
        }

        for (int i = 0; i < paddleWidth; i++) {
            if (paddleX + i < WIDTH) screen[paddleY][paddleX + i] = '█';
        }

        int bx = (int)ballX, by = (int)ballY;
        if (bx < WIDTH && by < HEIGHT) screen[by][bx] = '●';

        for (auto& eb : extraBalls) {
            int ex = (int)eb.first, ey = (int)eb.second;
            if (ex < WIDTH && ey < HEIGHT) screen[ey][ex] = '●';
        }

        for (auto& b : bonuses) {
            if (b.y < HEIGHT) screen[b.y][b.x] = '★';
        }

        for (auto& p : particles) {
            int px = (int)p.x, py = (int)p.y;
            if (px >= 0 && px < WIDTH && py >= 0 && py < HEIGHT) screen[py][px] = '·';
        }

        clearScreen();
        cout << string(WIDTH, '=') << endl;
        for (auto& row : screen) {
            for (char c : row) cout << c;
            cout << endl;
        }
        cout << string(WIDTH, '=') << endl;
        cout << " Score: " << score << "  Level: " << level << "  Lives: " << lives << "  ";
        if (gameOver) cout << " GAME OVER! Press R to restart ";
        cout << endl;
    }

    void spawnParticles(int x, int y) {
        for (int i = 0; i < 10; i++) {
            particles.push_back({
                x + ((double)rand() / RAND_MAX - 0.5) * 4,
                y + ((double)rand() / RAND_MAX - 0.5) * 4,
                ((double)rand() / RAND_MAX - 0.5) * 0.5,
                ((double)rand() / RAND_MAX - 0.5) * 0.5,
                rand() % 10 + 5
            });
        }
    }

    void applyBonus(const string& type) {
        if (type == "wide") {
            paddleWidth = min(paddleWidth + 4, 16);
        } else if (type == "slow") {
            ballDx *= 0.8;
            ballDy *= 0.8;
        } else if (type == "multi") {
            extraBalls.push_back({ballX, ballY});
        }
    }

public:
    Arkanoid3D() {
        srand(time(nullptr));
        resetGame();
    }

    void resetGame() {
        score = 0;
        lives = 3;
        level = 1;
        paddleX = WIDTH / 2 - PADDLE_WIDTH / 2;
        paddleWidth = PADDLE_WIDTH;
        ballX = WIDTH / 2;
        ballY = HEIGHT - 3;
        ballDx = (rand() % 2 == 0 ? 1 : -1) * 1.5;
        ballDy = -2.0;
        bricks = createBricks();
        bonuses.clear();
        extraBalls.clear();
        particles.clear();
        running = true;
        gameOver = false;
        paused = false;
        paddleY = HEIGHT - 1;
    }

    void update() {
        if (gameOver || paused) return;

        ballX += ballDx;
        ballY += ballDy;

        if (ballX <= 0 || ballX >= WIDTH - 1) ballDx = -ballDx;
        if (ballY <= 0) ballDy = -ballDy;

        if (ballY >= HEIGHT - 2 && ballX >= paddleX && ballX < paddleX + paddleWidth) {
            ballDy = -abs(ballDy);
            double offset = (ballX - (paddleX + paddleWidth / 2.0)) / (paddleWidth / 2.0);
            ballDx += offset * 0.5;
            if (ballDx > 2.5) ballDx = 2.5;
            if (ballDx < -2.5) ballDx = -2.5;
        }

        for (auto& b : bricks) {
            if (!b.alive) continue;
            if (ballX >= b.x && ballX < b.x + b.w && ballY >= b.y && ballY < b.y + b.h) {
                b.hp--;
                if (b.hp <= 0) {
                    b.alive = false;
                    score += 10;
                    spawnParticles(b.x + b.w / 2, b.y + b.h / 2);
                    if ((double)rand() / RAND_MAX < 0.15) {
                        string types[] = {"wide", "slow", "multi"};
                        bonuses.push_back({b.x + b.w / 2, b.y, types[rand() % 3]});
                    }
                } else {
                    score += 5;
                }
                ballDy = -ballDy;
                break;
            }
        }

        if (ballY >= HEIGHT) {
            if (!extraBalls.empty()) {
                auto eb = extraBalls.back();
                extraBalls.pop_back();
                ballX = eb.first;
                ballY = eb.second;
            } else {
                lives--;
                if (lives <= 0) gameOver = true;
                else {
                    ballX = WIDTH / 2;
                    ballY = HEIGHT - 3;
                    ballDx = (rand() % 2 == 0 ? 1 : -1) * 1.5;
                    ballDy = -2.0;
                }
            }
        }

        for (int i = bonuses.size() - 1; i >= 0; i--) {
            auto& b = bonuses[i];
            b.y++;
            if (b.y >= HEIGHT - 1) { bonuses.erase(bonuses.begin() + i); continue; }
            if (b.x >= paddleX && b.x < paddleX + paddleWidth && b.y >= HEIGHT - 2) {
                applyBonus(b.type);
                bonuses.erase(bonuses.begin() + i);
            }
        }

        for (int i = particles.size() - 1; i >= 0; i--) {
            auto& p = particles[i];
            p.x += p.dx; p.y += p.dy; p.life--;
            if (p.life <= 0) particles.erase(particles.begin() + i);
        }

        bool allDead = true;
        for (auto& b : bricks) if (b.alive) { allDead = false; break; }
        if (allDead) {
            level++;
            bricks = createBricks();
            ballX = WIDTH / 2;
            ballY = HEIGHT - 3;
            ballDx = (rand() % 2 == 0 ? 1 : -1) * 1.5;
            ballDy = -2.0;
            score += 50 * level;
        }
    }

    void run() {
        cout << "\033[36mArkanoid 3D - Use A/D or ←/→ to move, Q to quit, R to restart\033[0m" << endl;
        cout << "Press any key to start..." << endl;
        cin.get();

        // Set terminal to non-blocking input
        struct termios oldt, newt;
        tcgetattr(STDIN_FILENO, &oldt);
        newt = oldt;
        newt.c_lflag &= ~(ICANON | ECHO);
        tcsetattr(STDIN_FILENO, TCSANOW, &newt);
        fcntl(STDIN_FILENO, F_SETFL, O_NONBLOCK);

        while (running) {
            char ch;
            if (read(STDIN_FILENO, &ch, 1) > 0) {
                if (ch == 'q' || ch == 'Q') { running = false; break; }
                if ((ch == 'r' || ch == 'R') && gameOver) { resetGame(); running = true; continue; }
                if (ch == 'a' || ch == 'A') paddleX = max(0, paddleX - 2);
                if (ch == 'd' || ch == 'D') paddleX = min(WIDTH - paddleWidth, paddleX + 2);
            }

            update();
            draw();
            this_thread::sleep_for(chrono::milliseconds(FRAME_TIME));
        }

        tcsetattr(STDIN_FILENO, TCSANOW, &oldt);
        cout << "\033[33mGame Over! Final score: " << score << "\033[0m" << endl;
    }
};

int main() {
    Arkanoid3D game;
    game.run();
    return 0;
}
