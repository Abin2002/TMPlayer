// The FAQPage JSON-LD, in English: JsonLd moves its text into the page's language from the ld.*
// catalog strings. The questions are the ones on the page, in the same order.
export const faqLd = {
  "@context": "https://schema.org",
  "@type": "FAQPage",
  "mainEntity": [
    {
      "@type": "Question",
      "name": "Do I have to wait for the video to download?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "No. It starts in seconds and downloads while you watch. If your connection cannot keep up, a setting in the app will fetch the whole video first instead."
      }
    },
    {
      "@type": "Question",
      "name": "Is there a server or a bot involved?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "Neither. The app talks to Telegram directly through Telegram's official library. No media or account data passes through any TMPlayer server."
      }
    },
    {
      "@type": "Question",
      "name": "Which file do I download?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "Android TV or phone: the universal APK. Windows: one line in PowerShell, or the MSI installer. Mac with Apple Silicon: the DMG. Linux: the AppImage, or the PKGBUILD on Arch. The download page shows yours first."
      }
    },
    {
      "@type": "Question",
      "name": "Is there a Windows, macOS or Linux version?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "Yes: Windows 10 and 11, 64-bit Linux and, as a preview, Macs with Apple Silicon running macOS 26 or newer, all from the same release and played through mpv. There is no iPhone or iPad version."
      }
    },
    {
      "@type": "Question",
      "name": "Can I read my messages on it?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "No. It only ever shows the videos in your chats. You cannot read, reply or send anything from it."
      }
    },
    {
      "@type": "Question",
      "name": "Does TMPlayer provide any media of its own?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "No. It hosts, uploads, sells and recommends nothing. It shows the videos that are already in the chats and channels you have joined, and nothing else."
      }
    },
    {
      "@type": "Question",
      "name": "How much storage does it need?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "One video at a time. The previous one is cleared when you start a new one, so an 8 GB TV stick is enough."
      }
    },
    {
      "@type": "Question",
      "name": "Which video formats does it play?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "MKV, MP4, AVI and TS, with embedded subtitles including image-based ones, and multiple audio tracks you can switch during playback."
      }
    },
    {
      "@type": "Question",
      "name": "How does it update if it is not in the Play Store?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "It checks GitHub and asks before installing a newer release, on Android, Windows and Linux. On a Mac it tells you and opens the release page."
      }
    },
    {
      "@type": "Question",
      "name": "Is TMPlayer affiliated with Telegram?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "No. It is an unofficial, independent app, not affiliated with, endorsed by or connected to Telegram FZ-LLC. It uses TDLib, Telegram's own open source client library."
      }
    }
  ]
};
