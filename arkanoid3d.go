// arkanoid3d.go
package main

import (
	"fmt"
	"math"
	"math/rand"
	"os"
	"os/exec"
	"runtime"
	"time"
)

const (
	WIDTH        = 60
	HEIGHT       = 25
	PADDLE_WIDTH = 8
	FPS          = 30
)

type Brick struct {
	X, Y, W, H int
	HP         int
	Depth      float64
	Alive      bool
}

type Bonus struct {
	X, Y  int
	Type  string
	Alive bool
}

type Particle struct {
	X, Y  float64
	Dx, Dy float64
	Life  int
}

type Arkanoid3D struct {
	Score        int
	Lives        int
	Level        int
	PaddleX      int
	PaddleWidth  int
	BallX        float64
	BallY        float64
	BallDx       float64
	BallDy       float64
	Bricks       []Brick
	Bonuses      []Bonus
	ExtraBalls   [][4]float64
	Particles    []Particle
	Running      bool
	GameOver     bool
	Paused       bool
	PaddleY      int
	Keys         map[string]bool
}

func NewArkanoid3D() *Arkanoid3D {
	g := &Arkanoid3D{
		Keys: make(map[string]bool),
	}
	g.resetGame()
	return g
}

func (g *Arkanoid3D) resetGame() {
	g.Score = 0
	g.Lives = 3
	g.Level = 1
	g.PaddleX = WIDTH/2 - PADDLE_WIDTH/2
	g.PaddleWidth = PADDLE_WIDTH
	g.BallX = float64(WIDTH / 2)
	g.BallY = float64(HEIGHT - 3)
	g.BallDx = (rand.Float64()*2 - 1) * 1.5
	g.BallDy = -2.0
	g.Bricks = g.createBricks()
	g.Bonuses = []Bonus{}
	g.ExtraBalls = [][4]float64{}
	g.Particles = []Particle{}
	g.Running = true
	g.GameOver = false
	g.Paused = false
	g.PaddleY = HEIGHT - 1
}

func (g *Arkanoid3D) createBricks() []Brick {
	var bricks []Brick
	rows := 3 + g.Level
	cols := WIDTH/4 - 1
	for r := 0; r < rows; r++ {
		for c := 0; c < cols; c++ {
			depth := float64(r) / float64(rows)
			w := 3 + int(depth*2)
			hp := 2
			if r >= 2 {
				hp = 1
			}
			bricks = append(bricks, Brick{
				X:     c*4 + 2,
				Y:     r*2 + 2,
				W:     w,
				H:     1,
				HP:    hp,
				Depth: depth,
				Alive: true,
			})
		}
	}
	return bricks
}

func (g *Arkanoid3D) clearScreen() {
	cmd := exec.Command("clear")
	if runtime.GOOS == "windows" {
		cmd = exec.Command("cmd", "/c", "cls")
	}
	cmd.Stdout = os.Stdout
	cmd.Run()
}

func (g *Arkanoid3D) draw() {
	screen := make([][]rune, HEIGHT)
	for i := range screen {
		screen[i] = make([]rune, WIDTH)
		for j := range screen[i] {
			screen[i][j] = ' '
		}
	}

	// Bricks
	for _, brick := range g.Bricks {
		if !brick.Alive {
			continue
		}
		var color string
		if brick.Depth < 0.3 {
			color = "\033[97m"
		} else if brick.Depth < 0.6 {
			color = "\033[36m"
		} else {
			color = "\033[34m"
		}
		if brick.HP == 2 {
			color = "\033[33m"
		}
		chars := []rune{'▓', '▒', '░', ' '}
		ch := chars[0]
		if brick.HP-1 < len(chars) && brick.HP-1 >= 0 {
			ch = chars[brick.HP-1]
		}
		for dy := 0; dy < brick.H; dy++ {
			for dx := 0; dx < brick.W; dx++ {
				x, y := brick.X+dx, brick.Y+dy
				if x < WIDTH && y < HEIGHT {
					screen[y][x] = rune(color[0])
				}
			}
		}
	}

	// Paddle
	for i := 0; i < g.PaddleWidth; i++ {
		if g.PaddleX+i < WIDTH {
			screen[g.PaddleY][g.PaddleX+i] = '█'
		}
	}

	// Ball
	if int(g.BallX) < WIDTH && int(g.BallY) < HEIGHT {
		screen[int(g.BallY)][int(g.BallX)] = '●'
	}

	// Extra balls
	for _, eb := range g.ExtraBalls {
		x, y := int(eb[0]), int(eb[1])
		if x < WIDTH && y < HEIGHT {
			screen[y][x] = '●'
		}
	}

	// Bonuses
	for _, bonus := range g.Bonuses {
		if bonus.Y < HEIGHT {
			screen[bonus.Y][bonus.X] = '★'
		}
	}

	// Particles
	for _, p := range g.Particles {
		x, y := int(p.X), int(p.Y)
		if x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT {
			screen[y][x] = '·'
		}
	}

	g.clearScreen()
	fmt.Println("=" + string(make([]byte, WIDTH)) + "=")
	for _, row := range screen {
		fmt.Println(string(row))
	}
	fmt.Println("=" + string(make([]byte, WIDTH)) + "=")
	ui := fmt.Sprintf(" Score: %d  Level: %d  Lives: %d  ", g.Score, g.Level, g.Lives)
	if g.GameOver {
		ui += " GAME OVER! Press R to restart "
	}
	fmt.Println(ui)
}

func (g *Arkanoid3D) update() {
	if g.GameOver || g.Paused {
		return
	}

	g.BallX += g.BallDx
	g.BallY += g.BallDy

	// Walls
	if g.BallX <= 0 || g.BallX >= float64(WIDTH-1) {
		g.BallDx = -g.BallDx
	}
	if g.BallY <= 0 {
		g.BallDy = -g.BallDy
	}

	// Paddle
	if g.BallY >= float64(HEIGHT-2) && int(g.BallX) >= g.PaddleX && int(g.BallX) < g.PaddleX+g.PaddleWidth {
		g.BallDy = -math.Abs(g.BallDy)
		offset := (g.BallX - float64(g.PaddleX+g.PaddleWidth/2)) / float64(g.PaddleWidth/2)
		g.BallDx += offset * 0.5
		if g.BallDx > 2.5 {
			g.BallDx = 2.5
		}
		if g.BallDx < -2.5 {
			g.BallDx = -2.5
		}
	}

	// Bricks
	for i := range g.Bricks {
		brick := &g.Bricks[i]
		if !brick.Alive {
			continue
		}
		if int(g.BallX) >= brick.X && int(g.BallX) < brick.X+brick.W &&
			int(g.BallY) >= brick.Y && int(g.BallY) < brick.Y+brick.H {
			brick.HP--
			if brick.HP <= 0 {
				brick.Alive = false
				g.Score += 10
				g.spawnParticles(float64(brick.X+brick.W/2), float64(brick.Y+brick.H/2))
				if rand.Float64() < 0.15 {
					types := []string{"wide", "slow", "multi"}
					g.Bonuses = append(g.Bonuses, Bonus{
						X:    brick.X + brick.W/2,
						Y:    brick.Y,
						Type: types[rand.Intn(3)],
						Alive: true,
					})
				}
			} else {
				g.Score += 5
			}
			g.BallDy = -g.BallDy
			break
		}
	}

	// Ball lost
	if g.BallY >= float64(HEIGHT) {
		if len(g.ExtraBalls) > 0 {
			eb := g.ExtraBalls[0]
			g.ExtraBalls = g.ExtraBalls[1:]
			g.BallX, g.BallY, g.BallDx, g.BallDy = eb[0], eb[1], eb[2], eb[3]
		} else {
			g.Lives--
			if g.Lives <= 0 {
				g.GameOver = true
			} else {
				g.BallX = float64(WIDTH / 2)
				g.BallY = float64(HEIGHT - 3)
				g.BallDx = (rand.Float64()*2 - 1) * 1.5
				g.BallDy = -2.0
			}
		}
	}

	// Bonuses
	for i := len(g.Bonuses) - 1; i >= 0; i-- {
		bonus := &g.Bonuses[i]
		bonus.Y++
		if bonus.Y >= HEIGHT-1 {
			g.Bonuses = append(g.Bonuses[:i], g.Bonuses[i+1:]...)
			continue
		}
		if bonus.X >= g.PaddleX && bonus.X < g.PaddleX+g.PaddleWidth && bonus.Y >= HEIGHT-2 {
			g.applyBonus(bonus.Type)
			g.Bonuses = append(g.Bonuses[:i], g.Bonuses[i+1:]...)
		}
	}

	// Particles
	for i := len(g.Particles) - 1; i >= 0; i-- {
		p := &g.Particles[i]
		p.X += p.Dx
		p.Y += p.Dy
		p.Life--
		if p.Life <= 0 {
			g.Particles = append(g.Particles[:i], g.Particles[i+1:]...)
		}
	}

	// Level complete
	allDead := true
	for _, b := range g.Bricks {
		if b.Alive {
			allDead = false
			break
		}
	}
	if allDead {
		g.Level++
		g.Bricks = g.createBricks()
		g.BallX = float64(WIDTH / 2)
		g.BallY = float64(HEIGHT - 3)
		g.BallDx = (rand.Float64()*2 - 1) * 1.5
		g.BallDy = -2.0
		g.Score += 50 * g.Level
	}
}

func (g *Arkanoid3D) applyBonus(bonusType string) {
	switch bonusType {
	case "wide":
		g.PaddleWidth += 4
		if g.PaddleWidth > 16 {
			g.PaddleWidth = 16
		}
	case "slow":
		g.BallDx *= 0.8
		g.BallDy *= 0.8
	case "multi":
		g.ExtraBalls = append(g.ExtraBalls, [4]float64{g.BallX, g.BallY, -g.BallDx, g.BallDy})
	}
}

func (g *Arkanoid3D) spawnParticles(x, y float64) {
	for i := 0; i < 10; i++ {
		g.Particles = append(g.Particles, Particle{
			X:    x + (rand.Float64()-0.5)*4,
			Y:    y + (rand.Float64()-0.5)*4,
			Dx:   (rand.Float64() - 0.5) * 0.5,
			Dy:   (rand.Float64() - 0.5) * 0.5,
			Life: rand.Intn(10) + 5,
		})
	}
}

func (g *Arkanoid3D) run() {
	fmt.Println("\033[36mArkanoid 3D - Use A/D or ←/→ to move, Q to quit, R to restart\033[0m")
	fmt.Println("Press any key to start...")
	fmt.Scanln()

	g.Running = true

	// Keyboard input in separate goroutine
	go func() {
		for g.Running {
			var input string
			fmt.Scanln(&input)
			if input == "q" || input == "Q" {
				g.Running = false
				return
			}
			if (input == "r" || input == "R") && g.GameOver {
				g.resetGame()
				g.Running = true
			}
			if input == "a" || input == "A" || input == "\x1b[D" {
				g.PaddleX -= 2
				if g.PaddleX < 0 {
					g.PaddleX = 0
				}
			}
			if input == "d" || input == "D" || input == "\x1b[C" {
				g.PaddleX += 2
				if g.PaddleX > WIDTH-g.PaddleWidth {
					g.PaddleX = WIDTH - g.PaddleWidth
				}
			}
		}
	}()

	ticker := time.NewTicker(time.Second / FPS)
	for g.Running {
		g.update()
		g.draw()
		<-ticker.C
	}
	fmt.Printf("\033[33mGame Over! Final score: %d\033[0m\n", g.Score)
}

func main() {
	rand.Seed(time.Now().UnixNano())
	game := NewArkanoid3D()
	game.run()
}
