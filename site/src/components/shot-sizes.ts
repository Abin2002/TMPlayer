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

// The home page's showcase: the television takes the hero's width up to 1056 px, and the phone
// leaning on it is a fifth of that on a narrow screen and a sixth on a wide one (`.showcase`).
// Under 600 px the television is the viewport less 48 px of gutter and bezel, measured: 90vw there
// asked a 412 px phone at 1.75x for 649 px, one past the 640 px copy, and it was sent the 960.
export const SHOT_HERO_TV = '(min-width: 1180px) 1056px, (min-width: 600px) 90vw, calc(100vw - 48px)';
export const SHOT_HERO_PHONE = '(min-width: 1180px) 176px, (min-width: 701px) 15vw, 19vw';

// A screenshot in a drawn device (DeviceShot, TourShot): the phone is held narrow, everything else
// fills its column.
export const deviceSizes = (kind: string) => (kind === 'phone' ? SHOT_PHONE : SHOT_WIDE);
