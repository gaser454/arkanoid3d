// arkanoid3d.rs
use std::io::{self, Write, stdout, Read};
use std::thread;
use std::time::{Duration, Instant};
use std::sync::mpsc;
use termion::input::TermRead;
use termion::raw::IntoRawMode;
use rand::Rng;
use colored::*;

const WIDTH: usize = 60;
const HEIGHT: usize = 25;
const PADDLE_WIDTH: usize = 8;
const FPS: u64 = 30;

#[derive(Clone)]
struct Brick {
    x: usize,
    y: usize,
    w: usize,
    h: usize,
    hp: usize,
    depth: f64,
    alive: bool,
}

struct Bonus {
    x: usize,
    y: usize,
    typ: String,
}

struct Particle {
    x: f64,
    y: f64,
    dx: f64,
    dy: f64,
    life: i32,
}

struct Arkanoid3D {
    score: i32,
    lives: i32,
    level: i32,
    paddle_x: usize,
    paddle_width: usize,
    ball_x: f64,
    ball_y: f64,
    ball_dx: f64,
    ball_dy: f64,
    bricks: Vec<Brick>,
    bonuses: Vec<Bonus>,
    extra_balls: Vec<(f64, f64, f64, f64)>,
    particles: Vec<Particle>,
    running: bool,
    game_over: bool,
    paused: bool,
    paddle_y: usize,
}

impl Arkanoid3D {
    fn new() -> Self {
        let mut game = Arkanoid3D {
            score: 0,
            lives: 3,
            level: 1,
            paddle_x: WIDTH / 2 - PADDLE_WIDTH / 2,
            paddle_width: PADDLE_WIDTH,
            ball_x: (WIDTH / 2) as f64,
            ball_y: (HEIGHT - 3) as f64,
            ball_dx: rand::thread_rng().gen_range(-1.5..1.5).signum() * 1.5,
            ball_dy: -2.0,
            bricks: Vec::new(),
            bonuses: Vec::new(),
            extra_balls: Vec::new(),
            particles: Vec::new(),
            running: true,
            game_over: false,
            paused: false,
            paddle_y: HEIGHT - 1,
        };
        game.bricks = game.create_bricks();
        game
    }

    fn create_bricks(&self) -> Vec<Brick> {
        let mut bricks = Vec::new();
        let rows = 3 + self.level;
        let cols = WIDTH / 4 - 1;
        for r in 0..rows {
            for c in 0..cols {
                let depth = r as f64 / rows as f64;
                let w = 3 + (depth * 2.0) as usize;
                let hp = if r < 2 { 2 } else { 1 };
                bricks.push(Brick {
                    x: c * 4 + 2,
                    y: r * 2 + 2,
                    w,
                    h: 1,
                    hp,
                    depth,
                    alive: true,
                });
            }
        }
        bricks
    }

    fn draw(&self, stdout: &mut termion::raw::RawTerminal<std::io::Stdout>) {
        write!(stdout, "{}", termion::clear::All).unwrap();
        write!(stdout, "{}", termion::cursor::Goto(1, 1)).unwrap();

        let mut screen = vec![vec![' '; WIDTH]; HEIGHT];

        // Bricks
        for brick in &self.bricks {
            if !brick.alive { continue; }
            let color = if brick.depth < 0.3 { "white" } else if brick.depth < 0.6 { "cyan" } else { "blue" };
            let color_fn = match color {
                "white" => White::white,
                "cyan" => Cyan::cyan,
                _ => Blue::blue,
            };
            let ch = if brick.hp == 2 { '▓' } else { '▒' };
            for dy in 0..brick.h {
                for dx in 0..brick.w {
                    let x = brick.x + dx;
                    let y = brick.y + dy;
                    if x < WIDTH && y < HEIGHT {
                        screen[y][x] = ch;
                    }
                }
            }
        }

        // Paddle
        for i in 0..self.paddle_width {
            if self.paddle_x + i < WIDTH {
                screen[self.paddle_y][self.paddle_x + i] = '█';
            }
        }

        // Ball
        let bx = self.ball_x as usize;
        let by = self.ball_y as usize;
        if bx < WIDTH && by < HEIGHT {
            screen[by][bx] = '●';
        }

        // Extra balls
        for (bx, by, _, _) in &self.extra_balls {
            let bx = *bx as usize;
            let by = *by as usize;
            if bx < WIDTH && by < HEIGHT {
                screen[by][bx] = '●';
            }
        }

        // Bonuses
        for bonus in &self.bonuses {
            if bonus.y < HEIGHT {
                screen[bonus.y][bonus.x] = '★';
            }
        }

        // Particles
        for p in &self.particles {
            let x = p.x as usize;
            let y = p.y as usize;
            if x < WIDTH && y < HEIGHT {
                screen[y][x] = '·';
            }
        }

        // Render
        println!("{}", "=".repeat(WIDTH));
        for row in screen {
            let line: String = row.iter().collect();
            println!("{}", line);
        }
        println!("{}", "=".repeat(WIDTH));
        let ui = format!(" Score: {}  Level: {}  Lives: {}  ", self.score, self.level, self.lives);
        println!("{}", ui);
        stdout.flush().unwrap();
    }

    fn update(&mut self) {
        if self.game_over || self.paused { return; }

        self.ball_x += self.ball_dx;
        self.ball_y += self.ball_dy;

        // Walls
        if self.ball_x <= 0.0 || self.ball_x >= (WIDTH - 1) as f64 {
            self.ball_dx = -self.ball_dx;
        }
        if self.ball_y <= 0.0 {
            self.ball_dy = -self.ball_dy;
        }

        // Paddle
        if self.ball_y >= (HEIGHT - 2) as f64 && 
           self.ball_x as usize >= self.paddle_x && 
           self.ball_x as usize < self.paddle_x + self.paddle_width {
            self.ball_dy = -self.ball_dy.abs();
            let offset = (self.ball_x - (self.paddle_x + self.paddle_width / 2) as f64) / (self.paddle_width as f64 / 2.0);
            self.ball_dx += offset * 0.5;
            if self.ball_dx > 2.5 { self.ball_dx = 2.5; }
            if self.ball_dx < -2.5 { self.ball_dx = -2.5; }
        }

        // Bricks
        for brick in &mut self.bricks {
            if !brick.alive { continue; }
            if (self.ball_x as usize) >= brick.x && (self.ball_x as usize) < brick.x + brick.w &&
               (self.ball_y as usize) >= brick.y && (self.ball_y as usize) < brick.y + brick.h {
                brick.hp -= 1;
                if brick.hp <= 0 {
                    brick.alive = false;
                    self.score += 10;
                    self.spawn_particles((brick.x + brick.w / 2) as f64, (brick.y + brick.h / 2) as f64);
                    if rand::thread_rng().gen_bool(0.15) {
                        let types = vec!["wide", "slow", "multi"];
                        let typ = types[rand::thread_rng().gen_range(0..3)];
                        self.bonuses.push(Bonus {
                            x: brick.x + brick.w / 2,
                            y: brick.y,
                            typ: typ.to_string(),
                        });
                    }
                } else {
                    self.score += 5;
                }
                self.ball_dy = -self.ball_dy;
                break;
            }
        }

        // Ball lost
        if self.ball_y >= HEIGHT as f64 {
            if !self.extra_balls.is_empty() {
                let (bx, by, dx, dy) = self.extra_balls.remove(0);
                self.ball_x = bx;
                self.ball_y = by;
                self.ball_dx = dx;
                self.ball_dy = dy;
            } else {
                self.lives -= 1;
                if self.lives <= 0 {
                    self.game_over = true;
                } else {
                    self.ball_x = (WIDTH / 2) as f64;
                    self.ball_y = (HEIGHT - 3) as f64;
                    self.ball_dx = rand::thread_rng().gen_range(-1.5..1.5).signum() * 1.5;
                    self.ball_dy = -2.0;
                }
            }
        }

        // Bonuses
        let mut to_remove = Vec::new();
        for (i, bonus) in self.bonuses.iter_mut().enumerate() {
            bonus.y += 1;
            if bonus.y >= HEIGHT - 1 {
                to_remove.push(i);
                continue;
            }
            if bonus.x >= self.paddle_x && bonus.x < self.paddle_x + self.paddle_width && bonus.y >= HEIGHT - 2 {
                self.apply_bonus(&bonus.typ);
                to_remove.push(i);
            }
        }
        for i in to_remove.iter().rev() {
            self.bonuses.remove(*i);
        }

        // Particles
        let mut to_remove_p = Vec::new();
        for (i, p) in self.particles.iter_mut().enumerate() {
            p.x += p.dx;
            p.y += p.dy;
            p.life -= 1;
            if p.life <= 0 {
                to_remove_p.push(i);
            }
        }
        for i in to_remove_p.iter().rev() {
            self.particles.remove(*i);
        }

        // Level complete
        if !self.bricks.iter().any(|b| b.alive) {
            self.level += 1;
            self.bricks = self.create_bricks();
            self.ball_x = (WIDTH / 2) as f64;
            self.ball_y = (HEIGHT - 3) as f64;
            self.ball_dx = rand::thread_rng().gen_range(-1.5..1.5).signum() * 1.5;
            self.ball_dy = -2.0;
            self.score += 50 * self.level;
        }
    }

    fn apply_bonus(&mut self, typ: &str) {
        match typ {
            "wide" => {
                self.paddle_width += 4;
                if self.paddle_width > 16 { self.paddle_width = 16; }
            }
            "slow" => {
                self.ball_dx *= 0.8;
                self.ball_dy *= 0.8;
            }
            "multi" => {
                self.extra_balls.push((self.ball_x, self.ball_y, -self.ball_dx, self.ball_dy));
            }
            _ => {}
        }
    }

    fn spawn_particles(&mut self, x: f64, y: f64) {
        for _ in 0..10 {
            self.particles.push(Particle {
                x: x + rand::thread_rng().gen_range(-2.0..2.0),
                y: y + rand::thread_rng().gen_range(-2.0..2.0),
                dx: rand::thread_rng().gen_range(-0.5..0.5),
                dy: rand::thread_rng().gen_range(-0.5..0.5),
                life: rand::thread_rng().gen_range(5..15),
            });
        }
    }

    fn run(&mut self) {
        let mut stdout = io::stdout().into_raw_mode().unwrap();
        write!(stdout, "{}", termion::clear::All).unwrap();
        write!(stdout, "{}", termion::cursor::Goto(1, 1)).unwrap();
        println!("Arkanoid 3D - Use A/D or ←/→ to move, Q to quit, R to restart");
        println!("Press any key to start...");
        stdout.flush().unwrap();

        let stdin = io::stdin();
        let mut keys = stdin.keys();

        // Wait for key
        keys.next();

        let frame_duration = Duration::from_millis(1000 / FPS);
        while self.running {
            // Handle input
            while let Some(Ok(key)) = keys.next() {
                match key {
                    termion::event::Key::Char('q') | termion::event::Key::Char('Q') => {
                        self.running = false;
                    }
                    termion::event::Key::Char('r') | termion::event::Key::Char('R') if self.game_over => {
                        *self = Arkanoid3D::new();
                        self.running = true;
                    }
                    termion::event::Key::Char('a') | termion::event::Key::Char('A') | termion::event::Key::Left => {
                        if self.paddle_x >= 2 { self.paddle_x -= 2; }
                    }
                    termion::event::Key::Char('d') | termion::event::Key::Char('D') | termion::event::Key::Right => {
                        if self.paddle_x < WIDTH - self.paddle_width - 2 { self.paddle_x += 2; }
                    }
                    _ => {}
                }
            }

            let start = Instant::now();
            self.update();
            self.draw(&mut stdout);
            let elapsed = start.elapsed();
            if elapsed < frame_duration {
                thread::sleep(frame_duration - elapsed);
            }
        }
        println!("Game Over! Final score: {}", self.score);
    }
}

fn main() {
    let mut game = Arkanoid3D::new();
    game.run();
}
