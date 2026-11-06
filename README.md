# Bloom

A living pixel-art forest for your desktop. At a glance it is a dark, still clearing under the moon.
Watch for a while and you notice moss creeping, threads of mycelium feeling through the soil, and
flowers building themselves pixel by pixel.

## Using it

```
./bloom install     # start at login, add the fern icon and a settings entry to the menu, and start now
./bloom settings    # the game-style menu: settings, animal and plant picture books, field map and journal
./bloom status      # census of the forest: populations, how each species looks now, winning moss rules
./bloom journal     # the forest's own record of what has happened (add a number for more: journal 200)
./bloom summon      # call the white stag now, instead of waiting for it
./bloom summon fox  # or rabbit, snow, storm, aurora... (run ./bloom summon x for the full list, spoilers included)
./bloom stop | start | restart
./bloom reset       # bare ground, new growth (same landscape)
./bloom snapshot out.png
./bloom uninstall   # removes the autostart and menu entries; keeps your world
```

In the menu, use the mouse, or Tab to change page, arrow keys to move and adjust, Enter to select and Esc to close.
On the Animals page, click any individual to see its own traits, or step through them with `[` and `]`.

Settings live in `~/.config/bloom-wallpaper/config.toml`, and edits to that file also apply live.
The world is saved to `~/.local/state/bloom-wallpaper/` every five minutes and on exit, so it carries on
across reboots.

The **Forest seed** box in settings picks the landscape: type a number and press Grow, or press Randomize.
The forest you leave is kept, so entering its number again brings it back as it was, though it doesn't grow
while you're away. Forests with no saved state start fresh.

Needs only what Ubuntu Budgie already ships: Python 3.11+, GTK 3, gtk-layer-shell and pycairo, on a
Wayland compositor with wlr-layer-shell, such as labwc. X11 sessions are not supported.

## On a TV

`android/` holds Bloom by Reviiser, the same forest as a screensaver for Fire TV and Android TV, with the whole menu usable by remote. See [android/INSTALL.md](android/INSTALL.md). `android/build.sh` builds the APK, and `android/setup-tv.sh` installs it and sets it up. The tests in `android/test` grow the same forest in Python and Java and compare them.

## What is alive in there

**Soil.** A coarse grid of nutrients, moisture and shade. Almost nothing is created: nutrients move from
death to fungus to plant to death again, plus a trickle of leaf fall.

**Moss** is a Life-like cellular automaton, updated a few cells at a time. Every strain carries its own
birth/survival rule (`B34/S1234567`, say), passed on with occasional mutation. So the rules themselves
evolve, and `bloom status` shows which are winning. It spreads best in damp shade.

**Lichen** on bark and stone runs strict Conway B3/S23, reseeded with an R-pentomino or acorn when it
settles. Look closely at the trunks.

**Mycelium.** Colonies send out hyphal tips that forage toward dead matter. Kin fuse and strangers wall
each other off, though a much stronger colony can overrun a weaker one. Sparks running along the threads
are nutrients in transit. After rain, well-fed colonies fruit, and the mushroom's shape and colour come
from that colony's genes. Spores found new colonies with mutated, sometimes crossed, genes.

**Plants** behave as guilds. Clover fixes nitrogen and pioneers poor soil. Ferns and shrubs cast shade that
moss loves. Hungry species wait until the soil can feed them. Young trees need a fungal partner to grow well,
and dead ones become nurse logs that feed their neighbours. Seeds inherit size, form, flower colour and
preferred moisture and shade, so each spot slowly selects its own locals. Moths carry pollen between plants.

**Animals.** Fireflies nudge each other's flash timing, so scattered blinking drifts into rolling waves over
hours. Beetles graze moss and breed when well fed. An owl sometimes blinks on the bough. Every hour or two a
white stag crosses the clearing, walking around the pond, stopping to drink at the bank, nibbling plants and
dropping seeds picked up earlier.

**Evolution.** Animals are individuals with inherited genes, and most of each population lives out of sight in
the wider forest. Every five minutes they feed on what the clearing really holds, get hunted by predators that
really exist, and the survivors breed. Nothing scripts the result, only costs and benefits:

- *Coat:* predators spot you by contrast with the actual ground, moss included.
- *Glow and ornament* (antlers on a rabbit, a fox's plume): mates like them, but glow also draws predators and
  ornaments cost upkeep.
- *Size, speed and wariness:* each helps survival at a price in food.
- *Waking hours:* a line can shift between night and day to dodge different hunters and find different food.
- *Diet:* foxes can turn to frogs, songbirds to insects, beetles from moss to fungus.

Arms races run alongside. Grazed plants turn toxic (red flecks) at a cost to growth, and rabbits can evolve
tolerance. Fungal colonies can turn parasitic: an infected beetle wanders strangely, sprouts a pale stalk, and a
mushroom grows from where it dies, while beetles can evolve resistance. Crowds breed disease. Mates are chosen by
likeness, so one species can split in two. Species can die out, and strangers from the wider forest may eventually
wander in, carrying different genes. A rare large mutation can turn up nearly anything. Fireflies evolve their own
flash colour and brightness.

While the computer is off, the hidden populations carry on for up to a day of absence. Plants and the animals in
the clearing itself wait for you.

Timescales at the default pace: a rabbit can breed about half an hour after birth and lives a few hours, while a
fox takes over three hours to mature and lives about a day. Expect visible change over days, not minutes, and expect
different forests to end up different. `bloom journal` reports what it observes, and
is the easiest way to notice something you missed.

**Comings and goings.** Who turns up depends on who is alive and awake, so common animals are seen often and rare
ones rarely. Each one changes things while it's here:

- *Rabbits* nibble plants, fertilise the soil and carry clover and fern seed to the next meal.
- *Foxes* stalk rabbits slowly, then rush. Rabbits that notice in time get away. A fox with
  nothing to hunt sniffs around or drinks at the pond. Rabbits in the open tend to draw one in.
- *Owls* glide down from the bough to take a beetle, snail or frog.
- *Bats* hawk moths over the clearing.
- *Hedgehogs* snuffle after beetles and snails.
- *Songbirds* eat fruit and flowers, then sow those seeds at their next perch.
- *Squirrels* dig up moss to bury nuts. About a third are forgotten and come up as trees.
- *Moths and butterflies* are one population, split by the hours their genes keep. Both pollinate.
- *Frogs* live on the pond bank. They hop, dive and surface as a pair of eyes, and catch moths and fireflies with
  their tongues. They breed mostly in the rain.
- *Snails* come out after rain, leave silver trails and eat mushrooms.
- *Will-o'-the-wisps* (deep night) drift over the pond, drawing moths, setting off fireflies and feeding the
  fungi beneath them.
- *Gusts of wind* carry leaves, spores and moths across the clearing and sow seeds far from their parents.
- *Fairy rings* appear when a colony fruits in a circle. *Shooting stars* show only through gaps in the leaves.

`bloom status` keeps a tally of sightings.

**Sky.** The moon shows its real phase for today. Colour follows your clock through dawn, dim green day and dusk.

**Stars.** The bright stars are real, from a catalogue of 29 constellations. The view is centred on the moon with your
zenith straight up, so each star sits where it truly is relative to the moon for your place, date and time of night, and
the sky turns as the night goes on. They show only through gaps in the leaves, with no lines drawn between them. Your place comes from
your timezone's main city with no network or location service, or set `location` in the config. The painted moon is far
larger than life and shows every night, risen or not.

**On the trees.** Hanging moss slowly lengthens from the undersides of the great boughs, faster in wet weather, and sways in a gust. Squirrels run up the trunks and out along the bough, and pine martens hunt them there. Woodpeckers drill their way up, and treecreepers spiral round and round.

**Light.** A sun crosses the sky by your clock, seen through the gaps in the leaves, and the shafts of light swing with it. The sun and moon both give off a soft bloom and a row of lens-flare discs that swing around as the light moves, and fade behind leaves and cloud. The LENS FLARE slider sets the strength, or turns it off.

**Seasons** follow your real calendar (or pin one in settings). Spring greens the leaves, autumn turns them gold and
russet and piles fallen leaves on the ground, and winter strips them grey. Each season has its own plants: crocus,
foxglove, goldenrod and pumpkins, and snowdrops that bloom in the snow. Geese pass over in spring and autumn, dragonflies
hunt the pond in summer, ducks raise young in spring, and ermine run white across deep snow.

**Weather** includes slow clouds that pass behind the trees and in front of the moon and stars, rain, fog, thunderstorms (lightning can bring down a young tree), rainbows and winter auroras.
Snow settles in patches and caps the boughs, the pond freezes, and frogs go down into the mud.

**Winter never kills.** Plants and small animals go dormant and wait, those awake go hungry, and numbers fall, but a floor
keeps every species alive until spring. Tracks show in the snow, including from animals you never saw, and packed tracks
are the last thing to melt.

**Oddities.** Some things have their own rules: rare states of the ecosystem, the real moon, the hour on your clock,
the date. Most are only ever a shape against the sky. The menu's Oddities page keeps a collection, with a riddle for each
one you haven't found. Spoilers are in `wallbloom/cryptids.py`, if you want them.

**Colour and depth.** The NATURAL setting decides how much each thing keeps its own colour: blue sky, brown earth, green grass. Turned down, one tint from your palette takes over, more so by day. Plants and animals are shaded toward the light, and they, the trunks and the rocks cast soft shadows on the ground.

**CRT filter.** Off by default. The CRT FILTER slider adds the smear of a real beam, which melts checkerboard dithering into solid in-between colours as old pixel art expected, plus a shadow mask of red, green and blue phosphor dots, dark rounded edges and a faint hum bar rolling down the screen. It costs roughly 2 to 3.5% more of one core while on. They all strengthen together.

## Why it is cheap

About 2% of one core while fully visible, with the compositor adding about 0.4%, measured at 1920x1080.

- The screen is an indexed 8-bit image, as in old colour-cycling art. Changing time of day or darkness
  rebuilds a 256-entry table and re-translates the image with `bytes.translate`. The pond's shimmer is palette
  rotation, not animation.
- Only changed pixels are redrawn. A still forest sends the compositor nothing at all.
- The simulation samples a fixed amount of work each tick, so a crowded forest costs the same as an empty one.
- It runs at low scheduling priority. When a fullscreen window covers it, it stops drawing and falls to about
  0.7%. On battery it halves its frame rate.

Multiple monitors each show the same world, cropped to fit.
