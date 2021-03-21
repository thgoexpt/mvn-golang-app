package main

import (
	"image/color"
	"math/rand"
	"path/filepath"
	"time"

	"github.com/oakmound/oak/v2/render/mod"

	oak "github.com/oakmound/oak/v2"
	"github.com/oakmound/oak/v2/alg/floatgeom"
	"github.com/oakmound/oak/v2/collision"
	"github.com/oakmound/oak/v2/collision/ray"
	"github.com/oakmound/oak/v2/dlog"
	"github.com/oakmound/oak/v2/entities"
	"github.com/oakmound/oak/v2/event"
	"github.com/oakmound/oak/v2/key"
	"github.com/oakmound/oak/v2/mouse"
	"github.com/oakmound/oak/v2/physics"
	"github.com/oakmound/oak/v2/render"
	"github.com/oakmound/oak/v2/scene"
)

const (
	Enemy collision.Label = 1
)

var (
	playerAlive = true
	// Vectors are backed by pointers,
	// so despite this not being a pointer,
	// this does update according to the player's
	// position so long as we don't reset
	// the player's position vector
	playerPos physics.Vector

	sheet [][]*render.Sprite
)

const (
	fieldWidth  = 1000
	fieldHeight = 1000
)

func main() {

	oak.Add("tds", func(string, interface{}) {
		// Initialization
		playerAlive = true
		var err error
		sprites, err := render.GetSheet(filepath.Join("16x16", "sheet.png"))
		dlog.ErrorCheck(err)
		sheet = sprites.ToSprites()

		oak.SetViewportBounds(0, 0, fieldWidth, fieldHeight)

		// Player setup
		eggplant, err := render.GetSprite(filepath.Join("character", "eggplant-fish.png"))
		playerR := render.NewSwitch("left", map[string]render.Modifiable{
			"left": eggplant,
			// We must copy the sprite before we modify it, or "left"
			// will also be flipped.
			"right": eggplant.Copy().Modify(mod.FlipX),
		})
		if err != nil {
			dlog.Error(err)
		}
		char := entities.NewMoving(100, 100, 32, 32,
			playerR,
			nil, 0, 0)

		char.Speed = physics.NewVector(5, 5)
		playerPos = char.Point.Vector
		render.Draw(char.R, 1, 2)

		char.Bind(func(id int, _ interface{}) int {
			char := event.GetEntity(id).(*entities.Moving)
			char.Delta.Zero()
			if oak.IsDown(key.W) {
				char.Delta.ShiftY(-char.Speed.Y())
			}
			if oak.IsDown(key.A) {
				char.Delta.ShiftX(-char.Speed.X())
			}
			if oak.IsDown(key.S) {
				char.Delta.ShiftY(char.Speed.Y())
			}
			if oak.IsDown(key.D) {
				char.Delta.ShiftX(char.Speed.X())
			}
			char.ShiftPos(char.Delta.X(), char.Delta.Y())
			// Don't go out of bounds
			if char.X() < 0 {
				char.SetX(0)
			} else if char.X() > fieldWidth-char.W {
				char.SetX(fieldWidth - char.W)
			}
			if char.Y() < 0 {
				char.SetY(0)
			} else if char.Y() > fieldHeight-char.H {
				char.SetY(fieldHeight - char.H)
			}
			oak.SetScreen(
				int(char.R.X())-oak.ScreenWidth/2,
				int(char.R.Y())-oak.ScreenHeight/2,
			)
			hit := char.HitLabel(Enemy)
			if hit != nil {
				playerAlive = false
			}

			// update animation
			swtch := char.R.(*render.Switch)
			if char.Delta.X() > 0 {
				if swtch.Get() == "left" {
					swtch.Set("right")
				}
			} else if char.Delta.X() < 0 {
				if swtch.Get() == "right" {
					swtch.Set("left")
				}
			}

			return 0
		}, event.Enter)

		char.Bind(func(id int, me interface{}) int {
			char := event.GetEntity(id).(*entities.Moving)
			mevent := me.(mouse.Event)
			x := char.X() + char.W/2
			y := char.Y() + char.H/2
			mx := mevent.X() + float64(oak.ViewPos.X)
			my := mevent.Y() + float64(oak.ViewPos.Y)
			ray.DefaultCaster.CastDistance = floatgeom.Point2{x, y}.Sub(floatgeom.Point2{mx, my}).Magnitude()
			hits := ray.CastTo(floatgeom.Point2{x, y}, floatgeom.Point2{mx, my})
			for _, hit := range hits {
				hit.Zone.CID.Trigger("Destroy", nil)
			}
			render.DrawForTime(
				render.NewLine(x, y, mx, my, color.RGBA{0, 128, 0, 128}),
				time.Millisecond*50,
				1, 2)
			return 0
		}, mouse.Press)

		// Create enemies periodically
		event.GlobalBind(func(_ int, frames interface{}) int {
			f := frames.(int)
			if f%EnemyRefresh == 0 {
				NewEnemy()
			}
			return 0
		}, event.Enter)

		// Draw the background
		for x := 0; x < fieldWidth; x += 16 {
			for y := 0; y < fieldHeight; y += 16 {
				i := rand.Intn(3) + 1
				// Get a random tile to draw in this position
				sp := sheet[i/2][i%2].Copy()
				sp.SetPos(float64(x), float64(y))
				render.Draw(sp, 0, 1)
			}
		}

	}, func() bool {
		return playerAlive
	}, func() (string, *scene.Result) {
		return "tds", nil
	})

	// This indicates to oak to automatically open and load image and audio
	// files local to the project before starting any scene.
	oak.SetupConfig.BatchLoad = true

	render.SetDrawStack(
		render.NewCompositeR(),
		render.NewHeap(false),
		render.NewDrawFPS(),
		render.NewLogicFPS(),
	)

	oak.Init("tds")
}

// Top down shooter consts
const (
	EnemyRefresh = 25
	EnemySpeed   = 2
)

// NewEnemy creates an enemy for a top down shooter
func NewEnemy() {
	x, y := enemyPos()

	enemyFrame := sheet[0][0].Copy()
	enemyR := render.NewSwitch("left", map[string]render.Modifiable{
		"left":  enemyFrame,
		"right": enemyFrame.Copy().Modify(mod.FlipX),
	})
	enemy := entities.NewSolid(x, y, 16, 16,
		enemyR,
		nil, 0)

	render.Draw(enemy.R, 1, 2)

	enemy.UpdateLabel(Enemy)

	enemy.Bind(func(id int, _ interface{}) int {
		enemy := event.GetEntity(id).(*entities.Solid)
		// move towards the player
		x, y := enemy.GetPos()
		pt := floatgeom.Point2{x, y}
		pt2 := floatgeom.Point2{playerPos.X(), playerPos.Y()}
		delta := pt2.Sub(pt).Normalize().MulConst(EnemySpeed)
		enemy.ShiftPos(delta.X(), delta.Y())

		// update animation
		swtch := enemy.R.(*render.Switch)
		if delta.X() > 0 {
			if swtch.Get() == "left" {
				swtch.Set("right")
			}
		} else if delta.X() < 0 {
			if swtch.Get() == "right" {
				swtch.Set("left")
			}
		}
		return 0
	}, event.Enter)

	enemy.Bind(func(id int, _ interface{}) int {
		enemy := event.GetEntity(id).(*entities.Solid)
		enemy.Destroy()
		return 0
	}, "Destroy")
}

func enemyPos() (float64, float64) {
	// Spawn on the edge of the screen
	perimeter := fieldWidth*2 + fieldHeight*2
	pos := int(rand.Float64() * float64(perimeter))
	// Top
	if pos < fieldWidth {
		return float64(pos), 0
	}
	pos -= fieldWidth
	// Right
	if pos < fieldHeight {
		return float64(fieldWidth), float64(pos)
	}
	// Bottom
	pos -= fieldHeight
	if pos < fieldWidth {
		return float64(pos), float64(fieldHeight)
	}
	pos -= fieldWidth
	// Left
	return 0, float64(pos)
}
