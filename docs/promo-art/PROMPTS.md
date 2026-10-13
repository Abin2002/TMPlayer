# Promo artwork: prompts to generate

6 images for the promo build's demo content and the site screenshots. Every title is invented: no real films, people, brands or logos.

Fewer images, more use from each: every image is wide and text-free. Claude crops posters out of it and letters the titles itself (sharp, spelt right, in TMPlayer's own font), uses it whole as the backdrop, and takes episode thumbnails from different crops. Lesson thumbnails, channel avatars, clips and the other demo titles use the app's own generated covers, as real Telegram files without art do, so they need no images.

## For now: Unsplash stand-ins

Until these are generated, the same six file names are filled with Unsplash photos (no people's faces), credited in `docs/promo-art/CREDITS.md`. Generating an image later only means saving it under the same name in `promo-art-src/` and running `scripts/promo-art.py` again.

## How to use this file

1. Copy a prompt block into Codex as it is; the shared style is already in it.
2. Generate at 1536x1024 (3:2), landscape, for every image.
3. Save as PNG with exactly the file name shown into `promo-art-src/` at the repository root (git-ignored).
4. Check there is no stray text or watermark in the picture; regenerate if there is.
5. Tick the box. When all are done, tell Claude: it crops, letters, converts to WebP, wires them into the promo app and redoes the screenshots and the social card.

## Films (4): each becomes the film's backdrop and, cropped and lettered, its poster

### 1. `promo_saltwater_lanterns.png`

Saltwater Lanterns (drama)

- [ ] done

```text
Hundreds of paper lanterns floating over calm Kerala backwaters at dusk, a wooden canoe with two small silhouettes in the centre, coconut palms reflected in still water, warm orange sky fading to teal, festival mood. Wide 3:2 frame with the main subject centred, so a tall portrait crop from the middle still reads well; calm sky or space in the top third. Original cinematic artwork, rich colour grading, soft film grain, high detail. No text, no lettering, no logos, no watermarks, no real people or celebrity likeness, no brand names.
```

### 2. `promo_quiet_orbit.png`

The Quiet Orbit (sci-fi)

- [ ] done

```text
Inside a quiet space station greenhouse, a lone astronaut seen from behind tending rows of glowing plants in the centre, a large round window showing Earth at sunrise, cool blues with green light. Wide 3:2 frame with the main subject centred, so a tall portrait crop from the middle still reads well; calm sky or space in the top third. Original cinematic artwork, rich colour grading, soft film grain, high detail. No text, no lettering, no logos, no watermarks, no real people or celebrity likeness, no brand names.
```

### 3. `promo_monsoon_express.png`

Monsoon Express (thriller)

- [ ] done

```text
A night express train crossing a tall stone viaduct over a jungle gorge in heavy monsoon rain, one lit carriage window, lightning in the sky, headlight beams through the rain, deep blue and amber. Wide 3:2 frame with the main subject centred, so a tall portrait crop from the middle still reads well; calm sky or space in the top third. Original cinematic artwork, rich colour grading, soft film grain, high detail. No text, no lettering, no logos, no watermarks, no real people or celebrity likeness, no brand names.
```

### 4. `promo_paper_kingdoms.png`

Paper Kingdoms (animated family)

- [ ] done

```text
A whimsical city folded entirely from origami paper, a small paper fox standing on a paper bridge in the centre, paper birds and paper boats on a ribbon river, pastel colours, soft light, 3D animated film look. Wide 3:2 frame with the main subject centred, so a tall portrait crop from the middle still reads well; calm sky or space in the top third. Original cinematic artwork, rich colour grading, soft film grain, high detail. No text, no lettering, no logos, no watermarks, no real people or celebrity likeness, no brand names.
```

## Shows (2): one wide image each, cropped for the poster, the backdrop and the episode thumbnails

### 5. `promo_harbour_lines.png`

Harbour Lines (drama series), key art

- [ ] done

```text
A container port at golden hour, tall cranes and stacked colourful containers, a young dock worker and an older ship captain seen from behind in the centre looking at a ship leaving the harbour, warm gold and steel blue. Wide 3:2 frame with the main subject centred, so a tall portrait crop from the middle still reads well; calm sky or space in the top third. Original cinematic artwork, rich colour grading, soft film grain, high detail. No text, no lettering, no logos, no watermarks, no real people or celebrity likeness, no brand names.
```

### 6. `promo_skybound_academy.png`

Skybound Academy (anime series), key art

- [ ] done

```text
Hand-drawn anime style: a floating island school with towers above a sea of clouds, students on small gliders and airships around it, bright blue midday sky. Wide 3:2 frame with the main subject centred, so a tall portrait crop from the middle still reads well; calm sky or space in the top third. Original cinematic artwork, rich colour grading, soft film grain, high detail. No text, no lettering, no logos, no watermarks, no real people or celebrity likeness, no brand names.
```

