#!/usr/bin/env python3
# arkanoid3d.py
import random
import sys
import time
import os
from threading import Thread
from collections import deque
from colorama import init, Fore, Style, Back

init(autoreset=True)

WIDTH = 60
HEIGHT = 25
PADDLE_WIDTH = 8
PADDLE_CHAR = "█"
BALL_CHAR = "●"
BRICK_CHARS = ["▓", "▒", "░", " "]
FPS = 30
FRAME_TIME = 1.0 / FPS

class Arkanoid3D:
    def __init__(self):
        self.reset_game()

    def reset_game(self):
        self.score = 0
        self.lives = 3
        self.level = 1
        self.paddle_x = WIDTH // 2 - PADDLE_WIDTH // 2
        self.paddle_width = PADDLE_WIDTH
        self.ball_x = WIDTH // 2
        self.ball_y = HEIGHT - 3
        self.ball_dx = random.choice([-1, 1]) * 1.5
        self.ball_dy = -2.0
        self.bricks = self.create_bricks()
        self.bonuses = []
        self.extra_balls = []
        self.particles = []
        self.running = True
        self.paused = False
        self.game_over = False
        self.score_multiplier = 1
        self.clear_screen()

    def create_bricks(self):
        bricks = []
        rows = 3 + self.level
        cols = WIDTH // 4 - 1
        for r in range(rows):
            for c in range(cols):
                # 3D эффект: размер зависит от глубины
                depth = r / rows
                width = 3 + int(depth * 2)
                height = 1
                hp = 2 if r < 2 else 1
                bricks.append({
                    "x": c * 4 + 2,
                    "y": r * 2 + 2,
                    "w": width,
                    "h": height,
                    "hp": hp,
                    "depth": depth,
                    "alive": True
                })
        return bricks

    def clear_screen(self):
        os.system('cls' if os.name == 'nt' else 'clear')

    def draw(self):
        screen = [[' ' for _ in range(WIDTH)] for _ in range(HEIGHT)]
        
        # Рисуем блоки с 3D эффектом
        for brick in self.bricks:
            if not brick["alive"]:
                continue
            x, y, w, h, hp, depth = brick["x"], brick["y"], brick["w"], brick["h"], brick["hp"], brick["depth"]
            # Яркость зависит от глубины
            intensity = int(200 - depth * 150)
            color = Fore.WHITE if depth < 0.3 else Fore.CYAN if depth < 0.6 else Fore.BLUE
            if hp == 2:
                color = Fore.YELLOW
            for dy in range(h):
                for dx in range(w):
                    if x + dx < WIDTH and y + dy < HEIGHT:
                        screen[y + dy][x + dx] = color + BRICK_CHARS[min(hp-1, len(BRICK_CHARS)-1)] + Style.RESET_ALL

        # Платформа (с 3D эффектом)
        paddle_color = Fore.GREEN
        for i in range(self.paddle_width):
            if self.paddle_x + i < WIDTH:
                screen[self.paddle_y][self.paddle_x + i] = paddle_color + PADDLE_CHAR + Style.RESET_ALL

        # Мяч (3D эффект: размер зависит от скорости)
        ball_size = 1
        for dy in range(ball_size):
            for dx in range(ball_size):
                if 0 <= self.ball_x + dx < WIDTH and 0 <= self.ball_y + dy < HEIGHT:
                    screen[self.ball_y + dy][self.ball_x + dx] = Fore.RED + BALL_CHAR + Style.RESET_ALL

        # Дополнительные мячи
        for bx, by, dx, dy in self.extra_balls:
            for dy in range(ball_size):
                for dx in range(ball_size):
                    if 0 <= bx + dx < WIDTH and 0 <= by + dy < HEIGHT:
                        screen[by + dy][bx + dx] = Fore.MAGENTA + BALL_CHAR + Style.RESET_ALL

        # Бонусы
        for bonus in self.bonuses:
            if bonus["y"] < HEIGHT:
                screen[bonus["y"]][bonus["x"]] = Fore.YELLOW + "★" + Style.RESET_ALL

        # Частицы
        for p in self.particles:
            if 0 <= p["x"] < WIDTH and 0 <= p["y"] < HEIGHT:
                screen[p["y"]][p["x"]] = Fore.CYAN + "·" + Style.RESET_ALL

        # UI
        ui = f" Score: {self.score}  Level: {self.level}  Lives: {self.lives}  "
        if self.game_over:
            ui += " GAME OVER! Press R to restart "
        elif self.paused:
            ui += " PAUSED "
        
        # Рендеринг
        self.clear_screen()
        print("\n" + "=" * WIDTH)
        for row in screen:
            print("".join(row))
        print("=" * WIDTH)
        print(ui)

    def update(self):
        if self.game_over or self.paused:
            return

        # Движение мяча
        self.ball_x += self.ball_dx
        self.ball_y += self.ball_dy

        # Столкновение со стенами
        if self.ball_x <= 0 or self.ball_x >= WIDTH - 1:
            self.ball_dx = -self.ball_dx
        if self.ball_y <= 0:
            self.ball_dy = -self.ball_dy

        # Столкновение с платформой
        if self.ball_y >= HEIGHT - 2 and self.paddle_x <= self.ball_x < self.paddle_x + self.paddle_width:
            self.ball_dy = -abs(self.ball_dy)
            # Изменяем угол в зависимости от места попадания
            offset = (self.ball_x - (self.paddle_x + self.paddle_width / 2)) / (self.paddle_width / 2)
            self.ball_dx += offset * 0.5
            self.ball_dx = max(-2.5, min(2.5, self.ball_dx))

        # Столкновение с блоками
        for brick in self.bricks:
            if not brick["alive"]:
                continue
            bx, by, bw, bh = brick["x"], brick["y"], brick["w"], brick["h"]
            if bx <= self.ball_x < bx + bw and by <= self.ball_y < by + bh:
                brick["hp"] -= 1
                if brick["hp"] <= 0:
                    brick["alive"] = False
                    self.score += 10 * self.score_multiplier
                    self.spawn_particles(bx + bw//2, by + bh//2)
                    # Шанс выпадения бонуса
                    if random.random() < 0.15:
                        self.bonuses.append({"x": bx + bw//2, "y": by, "type": random.choice(["wide", "slow", "multi"])})
                else:
                    self.score += 5
                # Отскок
                self.ball_dy = -self.ball_dy
                break

        # Потеря мяча
        if self.ball_y >= HEIGHT:
            if self.extra_balls:
                # Используем дополнительный мяч
                self.ball_x, self.ball_y, self.ball_dx, self.ball_dy = self.extra_balls.pop(0)
            else:
                self.lives -= 1
                if self.lives <= 0:
                    self.game_over = True
                else:
                    self.ball_x = WIDTH // 2
                    self.ball_y = HEIGHT - 3
                    self.ball_dx = random.choice([-1, 1]) * 1.5
                    self.ball_dy = -2.0

        # Движение бонусов
        for bonus in self.bonuses[:]:
            bonus["y"] += 0.5
            if bonus["y"] >= HEIGHT - 1:
                self.bonuses.remove(bonus)
                continue
            if self.paddle_x <= bonus["x"] < self.paddle_x + self.paddle_width and bonus["y"] >= HEIGHT - 2:
                self.apply_bonus(bonus["type"])
                self.bonuses.remove(bonus)

        # Движение частиц
        for p in self.particles[:]:
            p["x"] += p["dx"]
            p["y"] += p["dy"]
            p["life"] -= 1
            if p["life"] <= 0:
                self.particles.remove(p)

        # Проверка победы
        if not any(b["alive"] for b in self.bricks):
            self.level += 1
            self.bricks = self.create_bricks()
            self.ball_x = WIDTH // 2
            self.ball_y = HEIGHT - 3
            self.ball_dx = random.choice([-1, 1]) * 1.5
            self.ball_dy = -2.0
            self.score += 50 * self.level

    def apply_bonus(self, bonus_type):
        if bonus_type == "wide":
            self.paddle_width = min(self.paddle_width + 4, 16)
        elif bonus_type == "slow":
            self.ball_dx *= 0.8
            self.ball_dy *= 0.8
        elif bonus_type == "multi":
            # Добавляем дополнительный мяч
            self.extra_balls.append([self.ball_x, self.ball_y, -self.ball_dx, self.ball_dy])

    def spawn_particles(self, x, y):
        for _ in range(10):
            self.particles.append({
                "x": x + random.randint(-2, 2),
                "y": y + random.randint(-2, 2),
                "dx": random.uniform(-0.5, 0.5),
                "dy": random.uniform(-0.5, 0.5),
                "life": random.randint(5, 15)
            })

    def handle_input(self):
        import msvcrt if os.name == 'nt' else select, sys, tty, termios
        # Неблокирующий ввод реализован через отдельный поток
        pass

    def run(self):
        self.paddle_y = HEIGHT - 1
        print(Fore.CYAN + "Arkanoid 3D - Use A/D or ←/→ to move, Q to quit, R to restart")
        print("Press any key to start...")
        input()
        self.running = True

        import threading
        import queue
        input_queue = queue.Queue()

        def get_input():
            while self.running:
                if os.name == 'nt':
                    import msvcrt
                    if msvcrt.kbhit():
                        ch = msvcrt.getch()
                        input_queue.put(ch.decode('utf-8', errors='ignore').lower())
                else:
                    import select, tty, termios, sys
                    fd = sys.stdin.fileno()
                    old = termios.tcgetattr(fd)
                    try:
                        tty.setraw(fd)
                        if select.select([sys.stdin], [], [], 0.05)[0]:
                            ch = sys.stdin.read(1)
                            input_queue.put(ch.lower())
                    finally:
                        termios.tcsetattr(fd, termios.TCSADRAIN, old)
                time.sleep(0.01)

        threading.Thread(target=get_input, daemon=True).start()

        while self.running:
            # Обработка ввода
            while not input_queue.empty():
                ch = input_queue.get()
                if ch == 'q':
                    self.running = False
                    return
                elif ch == 'r' and self.game_over:
                    self.reset_game()
                    self.running = True
                elif ch in 'ad' or ch in '\x1b[D' or ch in '\x1b[C':
                    if ch == 'a' or ch == '\x1b[D':
                        self.paddle_x = max(0, self.paddle_x - 2)
                    elif ch == 'd' or ch == '\x1b[C':
                        self.paddle_x = min(WIDTH - self.paddle_width, self.paddle_x + 2)

            self.update()
            self.draw()
            time.sleep(FRAME_TIME)

        print(Fore.YELLOW + "Game Over! Final score:", self.score)

if __name__ == "__main__":
    game = Arkanoid3D()
    game.run()
