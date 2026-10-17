// The FAQPage JSON-LD, in English: JsonLd moves its text into the page's language from the ld.*
// catalog strings. The questions are the ones on the page, in the same order.
export const faqLd = {
  "@context": "https://schema.org",
  "@type": "FAQPage",
  "mainEntity": [
    {
      "@type": "Question",
      "name": "Can I watch Telegram videos on Android TV?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "Yes. Telegram has no app for televisions, but TMPlayer runs on Android TV and Google TV with Android 8.0 or above: a Chromecast with Google TV, a Mi TV Stick, an NVIDIA Shield or a television with Android TV built in. Install it with the free Downloader app by typing tmplayer.org/apk, open it and scan the QR code with Telegram on your phone. Your chats and channels appear with their videos as tiles, and a video starts within seconds while it keeps downloading. It is a player for the videos in your chats, so messages stay on your phone."
      }
    },
    {
      "@type": "Question",
      "name": "Is there a Telegram app for Fire TV Stick?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "Not from Telegram: Amazon's store has no Telegram app for Fire TV. TMPlayer plays the videos in your Telegram chats on a Fire TV Stick, Fire TV Cube or Fire TV Edition television with Fire OS 7 or newer. It is installed with the free Downloader app and the remote: turn on Developer options, let Downloader install apps, type tmplayer.org/apk into it and install. Then open TMPlayer and scan its QR code with Telegram on your phone. It is tested on a stick with 1 GB of RAM, and it offers its own updates, so Downloader is only needed once."
      }
    },
    {
      "@type": "Question",
      "name": "How do I play Telegram videos on a PC or Mac?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "Install TMPlayer on Windows 10 or 11, on 64-bit Linux or, as a preview, on a Mac with Apple Silicon running macOS 26 or newer. On Windows, one line in PowerShell installs it, or use the MSI installer; on Linux, take the AppImage; on a Mac, open the DMG and drag TMPlayer into Applications. Sign in by scanning the QR code with Telegram on your phone, or with your phone number. Videos play through mpv while they download, with their subtitles, every audio track and keyboard shortcuts."
      }
    },
    {
      "@type": "Question",
      "name": "Is TMPlayer free? Is there a paid version?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "Yes, it is free, and there is no paid version. Every feature works for everyone, with no TMPlayer account, no subscription and no trial. The code is open source under the GPL-3.0, so anyone can read it and build it. There are no ads of its own; Telegram's sponsored messages still appear in public channels, as Telegram requires. The project is paid for by people who choose to: GitHub Sponsors and Buy Me a Coffee take any amount and unlock nothing."
      }
    },
    {
      "@type": "Question",
      "name": "Is TMPlayer safe? Can it read my messages?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "It talks to Telegram directly through TDLib, Telegram's official client library, with no TMPlayer server or bot in between. The sign-in shows up in Telegram's list of devices, where you can end it at any time. It only ever shows the videos in your chats: you cannot read, reply or send anything from it. There is no analytics or advertising library, and on Android crash reports go out only if you switch them on. All of the code is public under the GPL-3.0, every release is built by GitHub from that code, and the security page shows how to check the file you downloaded."
      }
    },
    {
      "@type": "Question",
      "name": "Is it the same as the official Telegram app?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "No. The official Telegram apps are messengers made by Telegram for phones and computers, and Telegram has no app for televisions. TMPlayer is an unofficial, independent player: it signs in to the same Telegram account through Telegram's own library and shows only the videos in your chats, laid out for a TV remote, a phone or a desktop window. It cannot send messages, make calls or change your account, so keep the official app for those. Both can be signed in to the same account at the same time."
      }
    },
    {
      "@type": "Question",
      "name": "Do I need a phone to sign in?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "Not always. You need your Telegram account and one of two ways in. The QR code, which a TV and a computer show first: in Telegram on your phone, open Settings, Devices, Link Desktop Device and scan it. Or Log in by phone number, which a phone shows first and a TV and a computer offer as the second choice: enter your number, then the code Telegram sends, usually to Telegram on a device that is already signed in, such as a computer, otherwise by text message or a call. If two-step verification is on, your Telegram password comes next."
      }
    },
    {
      "@type": "Question",
      "name": "Does it play music and audio files?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "No. TMPlayer lists videos only: Telegram videos, short silent clips, and files that Telegram calls video or whose names end in a video format such as MKV or MP4. Music, voice messages and audio files sent on their own, such as MP3 or FLAC, do not appear in it, so open those in Telegram. The sound inside a video is another matter: AAC, MP3, Opus, FLAC, Dolby Digital, DTS, TrueHD and the rest play, and a video with several audio tracks lets you switch between them during playback."
      }
    },
    {
      "@type": "Question",
      "name": "Can I use an external player for Telegram videos, such as VLC?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "Yes. TMPlayer is itself an external player for the videos in your Telegram chats: it signs in to your own account and plays them with their subtitles and every audio track, and remembers where you stopped, so there is no file to save and open elsewhere. On Android it can also pass a video on: the player's menu has Open in another app, which hands it to VLC, MX Player or any other player on the device. A finished download goes over whole, one still downloading goes over as far as it has got, and videos from chats that do not allow saving stay in TMPlayer. The desktop app plays everything itself, through mpv."
      }
    },
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
    },
    {
      "@type": "Question",
      "name": "Is TMPlayer the same app as TMP - Telegram Media Player?",
      "acceptedAnswer": {
        "@type": "Answer",
        "text": "No. TMP - Telegram Media Player is a different app from another developer, listed on Google Play since before TMPlayer existed. TMPlayer is a separate, open source app with its own code, made by dracu-lah, and its downloads come only from this site and its GitHub releases. Several apps call themselves a Telegram media player, so check the developer before you install one."
      }
    }
  ]
};
