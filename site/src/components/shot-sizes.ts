// How wide each kind of screenshot is drawn, as a `sizes` attribute, so a browser can pick the
// smallest copy that still fills it (Shot.astro writes the srcset). Each one was measured in the
// built site from 320 to 1920 px wide and rounds up a little, never down: a guess that is too small
// is a blurry picture, one that is too large is only a few kilobytes. Change the matching entry
// when the CSS that sizes one of these places changes.

// A television or a computer in the features gallery, a tour stop, or the Android TV page: the
// full column on a narrow screen, half of the 1160 px container from 900 px up (styles.css,
// `.shot`).
export const SHOT_WIDE = '(min-width: 1180px) 500px, (min-width: 900px) 44vw, 90vw';

// A phone in the features gallery, held to 280 px less its bezel (`.shot .device-phone`).
export const SHOT_PHONE = '(min-width: 360px) 272px, 76vw';

// The card shots on the Android TV page: one column, then two from 700 px, then three from
// 1000 px (`.card-grid-3`).
export const SHOT_CARD = '(min-width: 1180px) 280px, (min-width: 1000px) 24vw, (min-width: 700px) 40vw, calc(100vw - 88px)';

// The QR sign-in under a step on the home page, at most 360 px (`.step-shot`).
export const SHOT_STEP = '(min-width: 450px) 360px, calc(100vw - 88px)';

// The home page's hero stage (`.hstage`): from 1080 px it is the hero's end column, under that it is
// the container's width up to 640 px. The television takes 84 per cent of it, all of it on a phone,
// and each of the three fanned phones a quarter, or 30 per cent on a phone.
export const SHOT_HERO_TV = '(min-width: 1180px) 480px, (min-width: 1080px) calc(84vw - 500px), (min-width: 700px) 540px, calc(100vw - 48px)';
export const SHOT_HERO_PHONE = '(min-width: 1180px) 136px, (min-width: 1080px) calc(24vw - 140px), (min-width: 700px) 154px, 30vw';

// The home page's bento (`.hbento`): three columns of about 344 px from 1080 px, two under that,
// one on a phone, where a picture is the screen less 76 px of gutter, border and tile padding
// (measured). The player fills two columns everywhere, every other tile one.
export const SHOT_HOME_WIDE = '(min-width: 1180px) 680px, (min-width: 1080px) calc(67vw - 96px), (min-width: 700px) 90vw, calc(100vw - 76px)';
export const SHOT_HOME_CELL = '(min-width: 1180px) 320px, (min-width: 1080px) calc(34vw - 70px), (min-width: 700px) 45vw, calc(100vw - 76px)';

// The Chats screen beside the privacy points: half the container from 1080 px, the full width
// under that.
export const SHOT_HOME_TRUST = '(min-width: 1180px) 520px, (min-width: 1080px) calc(50vw - 64px), calc(100vw - 48px)';

// A screenshot in a drawn device (DeviceShot, TourShot): the phone is held narrow, everything else
// fills its column.
export const deviceSizes = (kind: string) => (kind === 'phone' ? SHOT_PHONE : SHOT_WIDE);

// The header pages (2026-10-20). A platform card's thumbnail on the download page, 72 px tall and
// at most 240 px wide, hidden under 600 px (`.platform-shot`).
export const SHOT_PLATFORM = '(min-width: 600px) 240px, 1px';
// The download page's television and phone beside its heading, from 1080 px only (`.dl-hero`).
export const SHOT_DL_TV = '(min-width: 1080px) 460px, 1px';
export const SHOT_DL_PHONE = '(min-width: 1080px) 110px, 1px';
// The features page's bento of screens: a wide tile two columns of three, a tall tile one, and on a
// phone a card of the sideways row, 82 per cent of the screen (`.bento`).
export const SHOT_BENTO_WIDE = '(min-width: 1180px) 700px, (min-width: 900px) 60vw, 84vw';
export const SHOT_BENTO_TALL = '(min-width: 1180px) 340px, (min-width: 900px) 30vw, 84vw';
// A small picture in a feature tile on the features page (`.feat-shot`).
export const SHOT_FEAT = '(min-width: 1180px) 340px, (min-width: 900px) 30vw, (min-width: 600px) 45vw, calc(100vw - 72px)';
// A screenshot beside an install step, at most 420 px (`.step-pic`).
export const SHOT_STEP_PIC = '(min-width: 520px) 420px, calc(100vw - 96px)';
