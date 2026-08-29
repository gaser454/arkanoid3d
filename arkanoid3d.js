#!/usr/bin/env node
// arkanoid3d.js
const readline = require('readline');
const chalk = require('chalk');
const { spawn } = require('child_process');

const WIDTH = 60;
const HEIGHT = 25;
const PADDLE_WIDTH = 8;
const FPS = 30;
const FRAME_TIME = 1000 / FPS;

class Arkanoid3D {
    constructor() {
        this.resetGame();
        this.rl = readline.createInterface({
            input: process.stdin,
            output: process.stdout
        });
        readline.emitKeypressEvents(process.stdin);
        process.stdin.setRawMode(true);
    }

    resetGame() {
        this.score = 0;
        this.lives = 3;
        this.level = 1;
        this.paddleX = Math.floor(WIDTH / 2 - PADDLE_WIDTH / 2);
        this.paddleWidth = PADDLE_WIDTH;
        this.ballX = Math.floor(WIDTH / 2);
        this.ballY = HEIGHT - 3;
        this.ballDx = (Math.random() > 0.5 ? 1 : -1) * 1.5;
        this.ballDy = -2.0;
        this.bricks = this.createBricks();
        this.bonuses = [];
        this.extraBalls = [];
        this.particles = [];
        this.running = true;
        this.gameOver = false;
        this.paused = false;
        this.keys = {};
        this.scoreMultiplier = 1;
    }

    createBricks() {
        const bricks = [];
        const rows = 3 + this.level;
        const cols = Math.floor(WIDTH / 4) - 1;
        for (let r = 0; r < rows; r++) {
            for (let c = 0; c < cols; c++) {
                const depth = r / rows;
                const w = 3 + Math.floor(depth * 2);
                const hp = r < 2 ? 2 : 1;
                bricks.push({
                    x: c * 4 + 2,
                    y: r * 2 + 2,
                    w: w,
                    h: 1,
                    hp: hp,
                    depth: depth,
                    alive: true
                });
            }
        }
        return bricks;
    }

    clearScreen() {
        console.clear();
    }

    draw() {
        const screen = Array.from({ length: HEIGHT }, () => Array(WIDTH).fill(' '));

        // Bricks with 3D effect
        for (const brick of this.bricks) {
            if (!brick.alive) continue;
            const { x, y, w, h, hp, depth } = brick;
            let color = chalk.white;
            if (depth < 0.3) color = chalk.white;
            else if (depth < 0.6) color = chalk.cyan;
            else color = chalk.blue;
            if (hp === 2) color = chalk.yellow;
            const chars = ['▓', '▒', '░', ' '];
            const ch = chars[Math.min(hp - 1, chars.length - 1)];
            for (let dy = 0; dy < h; dy++) {
                for (let dx = 0; dx < w; dx++) {
                    if (x + dx < WIDTH && y + dy < HEIGHT) {
                        screen[y + dy][x + dx] = color(ch);
                    }
                }
            }
        }

        // Paddle
        for (let i = 0; i < this.paddleWidth; i++) {
            if (this.paddleX + i < WIDTH) {
                screen[this.paddleY][this.paddleX + i] = chalk.green('█');
            }
        }

        // Ball
        if (this.ballX >= 0 && this.ballX < WIDTH && this.ballY >= 0 && this.ballY < HEIGHT) {
            screen[this.ballY][this.ballX] = chalk.red('●');
        }

        // Extra balls
        for (const [bx, by] of this.extraBalls) {
            if (bx >= 0 && bx < WIDTH && by >= 0 && by < HEIGHT) {
                screen[by][bx] = chalk.magenta('●');
            }
        }

        // Bonuses
        for (const bonus of this.bonuses) {
            if (bonus.y < HEIGHT) {
                screen[bonus.y][bonus.x] = chalk.yellow('★');
            }
        }

        // Particles
        for (const p of this.particles) {
            if (p.x >= 0 && p.x < WIDTH && p.y >= 0 && p.y < HEIGHT) {
                screen[p.y][p.x] = chalk.cyan('·');
            }
        }

        // Render
        this.clearScreen();
        console.log('='.repeat(WIDTH));
        for (const row of screen) {
            console.log(row.join(''));
        }
        console.log('='.repeat(WIDTH));
        let ui = ` Score: ${this.score}  Level: ${this.level}  Lives: ${this.lives}  `;
        if (this.gameOver) ui += ' GAME OVER! Press R to restart ';
        console.log(ui);
    }

    update() {
        if (this.gameOver || this.paused) return;

        this.ballX += this.ballDx;
        this.ballY += this.ballDy;

        // Walls
        if (this.ballX <= 0 || this.ballX >= WIDTH - 1) this.ballDx = -this.ballDx;
        if (this.ballY <= 0) this.ballDy = -this.ballDy;

        // Paddle collision
        if (this.ballY >= HEIGHT - 2 && this.ballX >= this.paddleX && this.ballX < this.paddleX + this.paddleWidth) {
            this.ballDy = -Math.abs(this.ballDy);
            const offset = (this.ballX - (this.paddleX + this.paddleWidth / 2)) / (this.paddleWidth / 2);
            this.ballDx += offset * 0.5;
            this.ballDx = Math.max(-2.5, Math.min(2.5, this.ballDx));
        }

        // Brick collision
        for (const brick of this.bricks) {
            if (!brick.alive) continue;
            const { x, y, w, h } = brick;
            if (this.ballX >= x && this.ballX < x + w && this.ballY >= y && this.ballY < y + h) {
                brick.hp--;
                if (brick.hp <= 0) {
                    brick.alive = false;
                    this.score += 10 * this.scoreMultiplier;
                    this.spawnParticles(x + w/2, y + h/2);
                    if (Math.random() < 0.15) {
                        this.bonuses.push({ x: x + w/2, y: y, type: ['wide', 'slow', 'multi'][Math.floor(Math.random() * 3)] });
                    }
                } else {
                    this.score += 5;
                }
                this.ballDy = -this.ballDy;
                break;
            }
        }

        // Ball lost
        if (this.ballY >= HEIGHT) {
            if (this.extraBalls.length > 0) {
                const [bx, by, dx, dy] = this.extraBalls.pop();
                this.ballX = bx;
                this.ballY = by;
                this.ballDx = dx;
                this.ballDy = dy;
            } else {
                this.lives--;
                if (this.lives <= 0) {
                    this.gameOver = true;
                } else {
                    this.ballX = Math.floor(WIDTH / 2);
                    this.ballY = HEIGHT - 3;
                    this.ballDx = (Math.random() > 0.5 ? 1 : -1) * 1.5;
                    this.ballDy = -2.0;
                }
            }
        }

        // Bonuses
        for (let i = this.bonuses.length - 1; i >= 0; i--) {
            const bonus = this.bonuses[i];
            bonus.y += 0.5;
            if (bonus.y >= HEIGHT - 1) {
                this.bonuses.splice(i, 1);
                continue;
            }
            if (bonus.x >= this.paddleX && bonus.x < this.paddleX + this.paddleWidth && bonus.y >= HEIGHT - 2) {
                this.applyBonus(bonus.type);
                this.bonuses.splice(i, 1);
            }
        }

        // Particles
        for (let i = this.particles.length - 1; i >= 0; i--) {
            const p = this.particles[i];
            p.x += p.dx;
            p.y += p.dy;
            p.life--;
            if (p.life <= 0) {
                this.particles.splice(i, 1);
            }
        }

        // Level complete
        if (!this.bricks.some(b => b.alive)) {
            this.level++;
            this.bricks = this.createBricks();
            this.ballX = Math.floor(WIDTH / 2);
            this.ballY = HEIGHT - 3;
            this.ballDx = (Math.random() > 0.5 ? 1 : -1) * 1.5;
            this.ballDy = -2.0;
            this.score += 50 * this.level;
        }
    }

    applyBonus(type) {
        if (type === 'wide') {
            this.paddleWidth = Math.min(this.paddleWidth + 4, 16);
        } else if (type === 'slow') {
            this.ballDx *= 0.8;
            this.ballDy *= 0.8;
        } else if (type === 'multi') {
            this.extraBalls.push([this.ballX, this.ballY, -this.ballDx, this.ballDy]);
        }
    }

    spawnParticles(x, y) {
        for (let i = 0; i < 10; i++) {
            this.particles.push({
                x: x + (Math.random() - 0.5) * 4,
                y: y + (Math.random() - 0.5) * 4,
                dx: (Math.random() - 0.5) * 0.5,
                dy: (Math.random() - 0.5) * 0.5,
                life: Math.floor(Math.random() * 10) + 5
            });
        }
    }

    run() {
        console.log(chalk.cyan('Arkanoid 3D - Use A/D or ←/→ to move, Q to quit, R to restart'));
        console.log('Press any key to start...');
        
        process.stdin.on('keypress', (str, key) => {
            if (key && key.name === 'q') {
                this.running = false;
                process.exit(0);
            } else if (key && key.name === 'r' && this.gameOver) {
                this.resetGame();
                this.running = true;
            } else if (key) {
                if (key.name === 'a' || key.name === 'left') {
                    this.paddleX = Math.max(0, this.paddleX - 2);
                } else if (key.name === 'd' || key.name === 'right') {
                    this.paddleX = Math.min(WIDTH - this.paddleWidth, this.paddleX + 2);
                }
            }
        });

        this.paddleY = HEIGHT - 1;
        this.running = true;

        const gameLoop = () => {
            if (!this.running) return;
            this.update();
            this.draw();
            setTimeout(gameLoop, FRAME_TIME);
        };

        // Wait for keypress to start
        process.stdin.once('keypress', () => {
            gameLoop();
        });
    }
}

const game = new Arkanoid3D();
game.run();
