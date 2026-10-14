#!/usr/bin/env bash
# Builds mediamp-corresponding-source-0.5.0.tar.gz: the complete corresponding source of the mpv
# runtime that the desktop builds bundle through mediamp 0.5.0 (org.openani.mediamp, Open Ani).
#
# The runtime jars (mediamp-mpv-runtime-linux-x64 and -windows-x64) carry libmpv, FFmpeg and every
# shared library they need. mediamp builds libmpv, FFmpeg and dav1d from source; everything else is
# copied out of the build host's packages: Ubuntu 24.04 for Linux, MSYS2 UCRT64 for Windows. Each
# version below was proven against the shipped bytes, not guessed: the Linux libraries by GNU build
# ID against the Ubuntu .deb (patchelf rewrites RUNPATH, so the files differ but the build ID
# survives), the Windows DLLs by SHA-256 against the MSYS2 package, which they are byte for byte.
# glslang and SPIRV-Tools have no DLL of their own: MSYS2 links them statically into
# libshaderc_shared.dll, built on 2026-07-30 against the versions listed.
#
# Every file is fetched from a permanent address (the Ubuntu snapshot of 2026-09-19, the day
# mediamp 0.5.0 was released; MSYS2's source archive; GitHub archives of exact commits) and checked
# against the SHA-256 pinned here. A mismatch stops the script. Nothing is uploaded: attaching the
# archive to a release is a separate, manual step.
#
# With --macos it builds a separate archive, mediamp-corresponding-source-0.5.0-macos.tar.gz, for
# mediamp-mpv-runtime-macos-arm64 (the macOS DMG). mediamp builds that runtime on a self-hosted
# Apple Silicon runner: libmpv, FFmpeg and dav1d from the same pinned sources as above, and libass
# and libplacebo with their dependencies copied out of the runner's Homebrew (`brew install meson
# ninja libass libplacebo` in release.yml, then MpvTaskTypes.kt bundles every non-system dylib,
# rewrites the install names to @loader_path and signs them ad hoc). The rewrite and the new
# signature change the file bytes, so each dylib was proven instead by its LC_UUID and the SHA-256
# of its __TEXT,__text section, both equal to the dylib in the Homebrew arm64_sequoia bottle of
# the version listed (bottle SHA-256 in the comment beside it; neighbouring versions and rebuilds
# were fetched too and differ). Each formula file is taken from the homebrew-core commit that
# published that bottle, so it holds the bottle's SHA-256 as well as the source URL and patches.
# mpv's own build options and FFmpeg's configure line are embedded in libmpv.2.dylib and
# libavutil.60.dylib; they show -Dgpl=false, no --enable-gpl, mpv v0.41.0 and FFmpeg 8.0.1.
#
# Usage: scripts/build-mediamp-source.sh [--macos] [output dir]   (default: build/mediamp-source)
# Downloads are kept in <output dir>/downloads, so a second run fetches nothing.
set -euo pipefail

macos=false
if [ "${1:-}" = "--macos" ]; then
  macos=true
  shift
fi

VERSION="0.5.0"
NAME="mediamp-corresponding-source-$VERSION"
if $macos; then
  NAME="$NAME-macos"
fi

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
out="${1:-$root/build/mediamp-source}"
mkdir -p "$out"
out=$(cd "$out" && pwd)
cache="$out/downloads"
stage="$out/$NAME"

# A mediamp bump means a different runtime, so this archive would be the wrong one.
used="$(sed -n 's/^mediamp = "\(.*\)"$/\1/p' "$root/gradle/libs.versions.toml")"
if [ "$used" != "$VERSION" ]; then
  echo "mediamp is $used in libs.versions.toml, but this script covers $VERSION" >&2
  exit 1
fi

github="https://github.com"
msys2="https://repo.msys2.org/mingw/sources"
ubuntu="https://snapshot.ubuntu.com/ubuntu/20260919T000000Z"
brewcore="https://raw.githubusercontent.com/Homebrew/homebrew-core"

# directory in the archive, SHA-256, file name, address
upstream_manifest() {
  cat <<EOF
upstream f383b9d9ddcad983793c721d8d54f62019f99582499a18fb8915fbb3a5976288 mediamp-0.5.0.tar.gz $github/open-ani/mediamp/archive/refs/tags/v0.5.0.tar.gz
upstream 068960e89211f2adc80af03f637fa393fb5e407d3dd23592c8fe8c5490bb7ae4 mpv-0.41.0.tar.gz $github/mpv-player/mpv/archive/41f6a645068483470267271e1d09966ca3b9f413.tar.gz
upstream 269e43f5560fb647ee8df47b5b09fabaf3de2ad24dd0ee14c9f690b85b873ccc ffmpeg-8.0.1-449453a9.tar.gz $github/FFmpeg/FFmpeg/archive/449453a9713aa1af31a12e148fb0caba80ed02d7.tar.gz
upstream 9edb11a2108b375cc58370354e705feebc93430bb780130363815c1e1ac0c250 dav1d-1.5.4.tar.gz $github/videolan/dav1d/archive/54706fc6bc0cdecab7e9593974a4039cc038fca7.tar.gz
EOF
}

# macOS arm64 (--macos). How each bundled dylib was proven: its LC_UUID (second column) and the
# SHA-256 of its __TEXT,__text section are the same as in the dylib of the Homebrew arm64_sequoia
# bottle whose SHA-256 is in the last column (ghcr.io/v2/homebrew/core/<formula>/blobs/sha256:...).
# The formula file pinned below carries that same bottle SHA-256. Versions next to it were fetched
# and differ, as noted.
#   libglib-2.0.0      6bee9501347f38e9912c4d8d075e5ed8  glib 2.84.4                 8c27060502813360234e956090f1c6436f83769c422434e022921afa2f8c5f62
#   libintl.8          9414c1625ccc3b6d8a0dc7c743f7fec1  gettext 0.26 (0.26_1 differs) b25ac1e62443f8f79eadc3dadf6d3af954d4cbc33a0577fc626aa0c8f2bb01a5
#   libpcre2-8.0       cb5b10c2f523342eac8daeb83b1612c6  pcre2 10.46 (10.45 differs) 6ddb89f2eef25f73e258a97bf4379539e24ea76e207fa3c0eb49222bdb656de2
#   libharfbuzz.0      3818745daa553cdea12661d888401ebd  harfbuzz 11.4.5             caea11e390402d2201fa2430e4cff4a1fa0acec8b3c564f6ec792784e98fcd9a
#   libgraphite2.3.2.1 d07b94fcf3223e73a5e0727b6e8e8c90  graphite2 1.3.14 (rebuilds 1, 2 differ) 150b286ab4cfc8696fcd3fa4e7fa24c9825f024ef991899850b850e6f334100f
#   libfreetype.6      cfd169d0526f3a45b5db836906fec3d7  freetype 2.13.3 (2.14.0 differs) bcd39d3a523690cb0673df46122ff62763c5cd93bfff21bdcab856501d2dfb49
#   libpng16.16        1d4868683c8c321e97273a5ba5cd220e  libpng 1.6.50               0e84944536d6bf2c7cfd393a4576acf5c0ced03992d156685a7f83c7d2a60215
#   libfribidi.0       dd2ba6d9be7f3a9aa790384d5d8e799b  fribidi 1.0.16              4904bb3375c1b1f2539cb657fbf17b0d93c050904febbba51e9dd690f3540e3b
#   libunibreak.6      bec38d9767d43659bfe87c7b16b4949b  libunibreak 6.1             0bed33fbc272073291fa047e1f72422aff56e83276de789d02a0f3f82f117b45
#   libass.9           4ee550b266ab35e6a09aa2015c957c56  libass 0.17.4 (0.17.4_1 differs) d097cb72eb4e70bce4ca584649fa8874e4407da3067d6faa0dcc89013ffe1743
#   liblcms2.2         cd4b47155ce339d9a1b0f9e59af30f3a  little-cms2 2.17            3827b5a8583f0e92987bd0e7c721bda834157ad366d2cb66596559dd8654c9ac
#   libvulkan.1.4.326  e74f08459df83a3886237a812c87e886  vulkan-loader 1.4.326       2b3d377d43d59f2576e136e706395c6c64dff5e077fa43c4b0a921b615f635a5
#   libshaderc_shared.1 f102f2c153c73049a160af6eb41a0cc4 shaderc 2025.3 (2025.2, 2025.4 differ) 45d6afe8db7c6f5fd474d0197aad4cf53c1995db94fecf06be9f001b643a2bea
#   libplacebo.351     9e059c07c700313885bba36d0b7ee444  libplacebo 7.351.0, bottle rebuild 1 (rebuild 0 differs) 4e5f0af5ad2cb7d838cff63d56a50d7db5e9f7ca27c2045a546834397853240f
# shaderc links glslang, SPIRV-Tools and SPIRV-Headers statically, at the commits its formula pins
# (the dylib reports "glslang Khronos. 15.3.0", the version in glslang efd24d75's CHANGES.md).
# libplacebo's formula stages glad2, Jinja2 and MarkupSafe, which generate part of its source.
# The source tarballs and the glib patch are the ones each formula names, with its SHA-256; the
# three glslang/SPIRV archives are GitHub archives of the pinned commits.
macos_manifest() {
  cat <<EOF
macos/homebrew f309a36e72cd86b9b3bdf2c7c094e9fb0aa86bcb395acc38eff2bc1f7c15cf0d glib-2.84.4.rb $brewcore/74cde75b34835027a9de2933e534b617d0d573ea/Formula/g/glib.rb
macos/homebrew 57887e3ea6790688c4cab455588c16c111657126870fcbf624d48c0b5a6fd83f gettext-0.26.rb $brewcore/4095987e1869dfd88eed987fd04577717938b2e3/Formula/g/gettext.rb
macos/homebrew c4ced9680393b60ee737914dddcbffa3b37fa66d899c1dd2a6c90e4f1a120258 pcre2-10.46.rb $brewcore/4a738276ddfbf60f796ff7b8a7106cabe5fa4032/Formula/p/pcre2.rb
macos/homebrew b7572ebe63c21529e02b5b277b7429881d86fa93ec98e4fd9ba5f6ba123eac18 harfbuzz-11.4.5.rb $brewcore/9db4cbabe5d8edab76d53163a25c18f8506b8712/Formula/h/harfbuzz.rb
macos/homebrew 0ce2e3c9e15793e1ef839481f169a0106f42b1860e8b7eb2d0fcb54b6062e5da graphite2-1.3.14.rb $brewcore/9b26312fe7ceebdf774170484e7f889aa7993724/Formula/g/graphite2.rb
macos/homebrew f2869764c65717c9c708bb77a60453fb675cfbea9391f90a7fc9d2aae4289a75 freetype-2.13.3.rb $brewcore/aa4f0e9fe7abdff3e28e754ae90f50664c60fa09/Formula/f/freetype.rb
macos/homebrew 8e4de7a19c550f14160c0d5f8be77490972c3a032f871402b7d5909027776212 libpng-1.6.50.rb $brewcore/74b5b85b1a4d7243305812cdb10ae2ef4629a2ac/Formula/lib/libpng.rb
macos/homebrew 54693c5739662b7396217eeb9506e6538f1b5fd8de4fa19c93aa8732f73c252a fribidi-1.0.16.rb $brewcore/8633dd0356aedb86304b5bbd83f1b51e19622981/Formula/f/fribidi.rb
macos/homebrew ff7231cf9ea7bf491cc5530f34b8dacf9884d5c4c83db4d0d5a712b2c6b29f42 libunibreak-6.1.rb $brewcore/4b0c373d30d8e09c18fd7562776fa531c90d95ca/Formula/lib/libunibreak.rb
macos/homebrew 512bb269fc11890c340a702bb8f4ae1823c4673e7b99f0070cc9cd017362b989 libass-0.17.4.rb $brewcore/45ab754dcd6694699f0076f81ebabc1c927af095/Formula/lib/libass.rb
macos/homebrew b9bbd29b12c2d15ff7f2944158e669617c7c2bf89f6203298028ef6c8b10df3d little-cms2-2.17.rb $brewcore/3755f216851a0e0d0975db6d552427b37b09f1cd/Formula/l/little-cms2.rb
macos/homebrew a585e890286bc6de0a0933753b1e062dfc2ead3a137a570d88f152df3020662e vulkan-loader-1.4.326.rb $brewcore/5bf54585a0bb98381a4eac6aaeb2fb90d4b1c857/Formula/v/vulkan-loader.rb
macos/homebrew 6bd81ded76f9d9022a4f300ec84b925e84edf054bf8fa73f0687cd66afe4f012 shaderc-2025.3.rb $brewcore/9b06f6d97edff471bb87e4ec1c7305cda0856eea/Formula/s/shaderc.rb
macos/homebrew 3cad60a678b3eeb7642783b4697c7725a2b5b9ba8ba6925d4a9ff76d61c6cb6a libplacebo-7.351.0.rb $brewcore/599542601b6aa9ad2889ac280afe4dc0dcfb1cb8/Formula/lib/libplacebo.rb
macos/homebrew d846efd0bf62918350da94f850db33b0f8727fece9bfaf8164566e3094e80c97 glib-hardcoded-paths.diff https://raw.githubusercontent.com/Homebrew/formula-patches/b46d8deae6983110b4e39bb2971bcbd10bb59716/glib/hardcoded-paths.diff
macos 8a9ea10943c36fc117e253f80c91e477b673525ae45762942858aef57631bb90 glib-2.84.4.tar.xz https://download.gnome.org/sources/glib/2.84/glib-2.84.4.tar.xz
macos 39acf4b0371e9b110b60005562aace5b3631fed9b1bb9ecccfc7f56e58bb1d7f gettext-0.26.tar.gz https://ftp.gnu.org/gnu/gettext/gettext-0.26.tar.gz
macos 15fbc5aba6beee0b17aecb04602ae39432393aba1ebd8e39b7cabf7db883299f pcre2-10.46.tar.bz2 $github/PCRE2Project/pcre2/releases/download/pcre2-10.46/pcre2-10.46.tar.bz2
macos 5bc7a571b476eeda0c1996a04006da7c25f8edbc01cdf394ef729a6ecd1296d6 harfbuzz-11.4.5.tar.gz $github/harfbuzz/harfbuzz/archive/refs/tags/11.4.5.tar.gz
macos f99d1c13aa5fa296898a181dff9b82fb25f6cc0933dbaa7a475d8109bd54209d graphite2-1.3.14.tgz $github/silnrsi/graphite/releases/download/1.3.14/graphite2-1.3.14.tgz
macos 0550350666d427c74daeb85d5ac7bb353acba5f76956395995311a9c6f063289 freetype-2.13.3.tar.xz https://download.savannah.gnu.org/releases/freetype/freetype-2.13.3.tar.xz
macos 4df396518620a7aa3651443e87d1b2862e4e88cad135a8b93423e01706232307 libpng-1.6.50.tar.xz https://downloads.sourceforge.net/project/libpng/libpng16/1.6.50/libpng-1.6.50.tar.xz
macos 1b1cde5b235d40479e91be2f0e88a309e3214c8ab470ec8a2744d82a5a9ea05c fribidi-1.0.16.tar.xz $github/fribidi/fribidi/releases/download/v1.0.16/fribidi-1.0.16.tar.xz
macos cc4de0099cf7ff05005ceabff4afed4c582a736abc38033e70fdac86335ce93f libunibreak-6.1.tar.gz $github/adah1972/libunibreak/releases/download/libunibreak_6_1/libunibreak-6.1.tar.gz
macos 78f1179b838d025e9c26e8fef33f8092f65611444ffa1bfc0cfac6a33511a05a libass-0.17.4.tar.xz $github/libass/libass/releases/download/0.17.4/libass-0.17.4.tar.xz
macos d11af569e42a1baa1650d20ad61d12e41af4fead4aa7964a01f93b08b53ab074 lcms2-2.17.tar.gz https://downloads.sourceforge.net/project/lcms/lcms/2.17/lcms2-2.17.tar.gz
macos 86772b60eeef6f510586636b7cf7a0e0eabd9e9920bcabf6e8f3b1c2a634a4cc vulkan-loader-1.4.326.tar.gz $github/KhronosGroup/Vulkan-Loader/archive/refs/tags/v1.4.326.tar.gz
macos a8e4a25e5c2686fd36981e527ed05e451fcfc226bddf350f4e76181371190937 shaderc-2025.3.tar.gz $github/google/shaderc/archive/refs/tags/v2025.3.tar.gz
macos 9427deccbdf4bde6a269938df38c6bd75247493786a310d8d733a2c82065ef47 glslang-15.3.0-efd24d75.tar.gz $github/KhronosGroup/glslang/archive/efd24d75bcbc55620e759f6bf42c45a32abac5f8.tar.gz
macos 44d1005880c583fc00a0fb41c839214c68214b000ea8dcb54d352732fee600ff SPIRV-Tools-33e02568.tar.gz $github/KhronosGroup/SPIRV-Tools/archive/33e02568181e3312f49a3cf33df470bf96ef293a.tar.gz
macos c2225a49c3d7efa5c4f4ce4a6b42081e6ea3daca376f3353d9d7c2722d77a28a SPIRV-Headers-2a611a97.tar.gz $github/KhronosGroup/SPIRV-Headers/archive/2a611a970fdbc41ac2e3e328802aed9985352dca.tar.gz
macos d68159280842a7f0482dcea44a440f4c9a8e9403b82eccf185e46394dfc77e6a libplacebo-v7.351.0.tar.bz2 https://code.videolan.org/videolan/libplacebo/-/archive/v7.351.0/libplacebo-v7.351.0.tar.bz2
macos b84079b9fa404f37171b961bdd1d8da21370e6c818defb8481c5b3fe3d6436da glad2-2.0.8.tar.gz https://files.pythonhosted.org/packages/6e/5a/d62b24fe1c7c2f34e15c2aa4418a5327a8550fdc272999a59e0dddebc3ee/glad2-2.0.8.tar.gz
macos 0137fb05990d35f1275a587e9aee6d56da821fc83491a0fb838183be43f66d6d jinja2-3.1.6.tar.gz https://files.pythonhosted.org/packages/df/bf/f7da0350254c0ed7c72f3e33cef02e048281fec7ecec5f032d4aac52226b/jinja2-3.1.6.tar.gz
macos ee55d3edf80167e48ea11a923c7386f4669df67d7994554387f84e7d8b0a2bf0 markupsafe-3.0.2.tar.gz https://files.pythonhosted.org/packages/b2/97/5d42485e71dfc078108a86d6de8fa46db44a1a9295e89c5d6d4a06e23a62/markupsafe-3.0.2.tar.gz
EOF
}

desktop_manifest() {
  cat <<EOF
windows 4a68edae2c1454ffcf7cf0a6fccf6930c876caa5fe2422cdb0b34d1fc0f5fb03 mingw-w64-libass-0.17.5-1.src.tar.zst $msys2/mingw-w64-libass-0.17.5-1.src.tar.zst
windows 2f0f93875aac21990d20f1eaf0ad8b970eb6f186c663c77ae47aaaf9e5711fcf mingw-w64-brotli-1.2.0-1.src.tar.zst $msys2/mingw-w64-brotli-1.2.0-1.src.tar.zst
windows fd9360917314d1cbccb3cb15ed8450d1e56b8a9f733b606504aba52c52159e54 mingw-w64-bzip2-1.0.8-4.src.tar.zst $msys2/mingw-w64-bzip2-1.0.8-4.src.tar.zst
windows b60a8978d0e45f433ee8db7825d9ba90cc38956c89ab52055c05546f421942f9 mingw-w64-openssl-3.6.4-1.src.tar.zst $msys2/mingw-w64-openssl-3.6.4-1.src.tar.zst
windows 50e7303249bd57b9bb543e87e5de4b6331d744b22d2d26861bc8968ee60a70a3 mingw-w64-libdovi-3.4.0-1.src.tar.zst $msys2/mingw-w64-libdovi-3.4.0-1.src.tar.zst
windows 966058864d10c69f87e700e68586a31cacbfe5c2b50e84d62bcea3b05bd1fc16 mingw-w64-expat-2.8.4-2.src.tar.zst $msys2/mingw-w64-expat-2.8.4-2.src.tar.zst
windows 7df65dbf84397f4f71c6aa0bd39fac93f839d6b87f04a935773b69828740230d mingw-w64-fontconfig-2.18.3-1.src.tar.zst $msys2/mingw-w64-fontconfig-2.18.3-1.src.tar.zst
windows ee8071d0a6e82a274e959902898115f4c9c376a2a8e209d83dfe746c18004ec0 mingw-w64-freetype-2.14.3-1.src.tar.zst $msys2/mingw-w64-freetype-2.14.3-1.src.tar.zst
windows a496fd3c7973d443e741626e1be7af58d8adcbc1d4a1c056d07a011f0edc6d0b mingw-w64-fribidi-1.0.16-1.src.tar.zst $msys2/mingw-w64-fribidi-1.0.16-1.src.tar.zst
windows eb3479a8b0b23810fbbbc25ef76879e867e88d09960a40145d73f5505fda4da0 mingw-w64-gcc-16.2.0-3.src.tar.zst $msys2/mingw-w64-gcc-16.2.0-3.src.tar.zst
windows 580246fb9db6083e0191ac7fd0e44af71df6ec0dc52cfd384b5ae07f8665cc9e mingw-w64-glib2-2.90.0-1.src.tar.zst $msys2/mingw-w64-glib2-2.90.0-1.src.tar.zst
windows eb76ce274c68ea6bad0883144c21e44a803ec94fed9dc0f6584feb0a8415a1d6 mingw-w64-graphite2-1.3.15-1.src.tar.zst $msys2/mingw-w64-graphite2-1.3.15-1.src.tar.zst
windows 02dee132f33459b1a6089c225d70e4dd44f8cc816e78b6cc3a5355214b3ddb76 mingw-w64-harfbuzz-14.4.0-1.src.tar.zst $msys2/mingw-w64-harfbuzz-14.4.0-1.src.tar.zst
windows 74428280c17094da5b702c29b2e1a0abae59556ea5dfdd65705cc8ccc1e000fb mingw-w64-libiconv-1.19-1.src.tar.zst $msys2/mingw-w64-libiconv-1.19-1.src.tar.zst
windows cca0d0c8f60353faccc5ad40405240014e4a7ccc8b1c6ca9f4626aef3097d26e mingw-w64-gettext-1.0-1.src.tar.zst $msys2/mingw-w64-gettext-1.0-1.src.tar.zst
windows 83414d0d95c5cb2548d3050670694e9efc64304a52190f42bcad951efa038b41 mingw-w64-lcms2-2.19.1-1.src.tar.zst $msys2/mingw-w64-lcms2-2.19.1-1.src.tar.zst
windows 2d97640064e42c2d800580dc2e622487b6a2d980722380cdab5f5f71d311f39a mingw-w64-pcre2-10.48-3.src.tar.zst $msys2/mingw-w64-pcre2-10.48-3.src.tar.zst
windows 2abe10f1d16981407e8108c5ccb2e13cbeb28ce62f702a4caf1165b732e85bf0 mingw-w64-libplacebo-7.360.1-2.src.tar.zst $msys2/mingw-w64-libplacebo-7.360.1-2.src.tar.zst
windows 9e985fc4cef509ec689f982dc6a948e9a8ee5184db25d65e9dd4c21872092348 mingw-w64-libpng-1.6.58-1.src.tar.zst $msys2/mingw-w64-libpng-1.6.58-1.src.tar.zst
windows 847da728a130dd9b59c1d8228877f564ae7160483070894bf0536442b59a14c8 mingw-w64-shaderc-2026.3-1.src.tar.zst $msys2/mingw-w64-shaderc-2026.3-1.src.tar.zst
windows 948faf9909ad6ff3772077cd97546323415c4bd1164c7775e208bc8645780187 mingw-w64-glslang-16.3.0-1.src.tar.zst $msys2/mingw-w64-glslang-16.3.0-1.src.tar.zst
windows f872e2f63b443c3366e92394c564559b4e2e9813848af288f91ece9dabd105f5 mingw-w64-spirv-tools-3~1.4.357.0-1.src.tar.zst $msys2/mingw-w64-spirv-tools-3~1.4.357.0-1.src.tar.zst
windows 4082735b9f40527966fcadd19dfdcafd8313d5187abae5d53c270af448aa7c4f mingw-w64-spirv-cross-1~1.4.357.0-1.src.tar.zst $msys2/mingw-w64-spirv-cross-1~1.4.357.0-1.src.tar.zst
windows 3d51ed18b20deb475c6947ea8fdabb7d6107962e2e6e690de90e5227537728ad mingw-w64-libunibreak-7.0-1.src.tar.zst $msys2/mingw-w64-libunibreak-7.0-1.src.tar.zst
windows 7bf513784ae0bc4f1a413e1f787f7a3ba2bd7ec0503980ea4d17ddf0e4796c87 mingw-w64-winpthreads-14.0.0.r375.g9c1abbbf5-1.src.tar.zst $msys2/mingw-w64-winpthreads-14.0.0.r375.g9c1abbbf5-1.src.tar.zst
windows af28ead397f61609c07a8260279441b3e3113a42cbaaf04cec86933b65c60dc4 mingw-w64-vulkan-loader-1~1.4.357.0-1.src.tar.zst $msys2/mingw-w64-vulkan-loader-1~1.4.357.0-1.src.tar.zst
windows eef69dea52357e01b272d6fd6dc4d7c0773f71260cb7bcde6047c8c383518db3 mingw-w64-zlib-1.3.2-2.src.tar.zst $msys2/mingw-w64-zlib-1.3.2-2.src.tar.zst
linux 8ac2a33db1e08aa1d73f9022b71539f10f344bdea47b6f3dcf75186a1c1de27f libass_0.17.1-2build1.dsc $ubuntu/pool/universe/liba/libass/libass_0.17.1-2build1.dsc
linux d653be97198a0543c69111122173c41a99e0b91426f9e17f06a858982c2fb03d libass_0.17.1.orig.tar.gz $ubuntu/pool/universe/liba/libass/libass_0.17.1.orig.tar.gz
linux 699d1ba876d7bac95dbac56bac0f00126473e8588759a4ec13cdf52ae03d8483 libass_0.17.1.orig.tar.gz.asc $ubuntu/pool/universe/liba/libass/libass_0.17.1.orig.tar.gz.asc
linux 605dadc3d805d2d7bd197a7b21e6a7b8533eb9372f47e8690788ba96905556f4 libass_0.17.1-2build1.debian.tar.xz $ubuntu/pool/universe/liba/libass/libass_0.17.1-2build1.debian.tar.xz
linux c58421959677a80d942924685007e8d5b5d1160fd7f61a1f0a9062bf08835856 brotli_1.1.0-2build2.dsc $ubuntu/pool/main/b/brotli/brotli_1.1.0-2build2.dsc
linux 10973f4b4199eafa1d5735ef661ddb2ec2f97319ee9fd1824d4aabe08cff5265 brotli_1.1.0.orig.tar.gz $ubuntu/pool/main/b/brotli/brotli_1.1.0.orig.tar.gz
linux 96603222d7b6edb3253a846852972b6bde5b86814907d5540249e6950980a943 brotli_1.1.0-2build2.debian.tar.xz $ubuntu/pool/main/b/brotli/brotli_1.1.0-2build2.debian.tar.xz
linux ab5a03176ee106d3f0fa90e381da478ddae405918153cca248e682cd0c4a2269 bzip2_1.0.8.orig.tar.gz $ubuntu/pool/main/b/bzip2/bzip2_1.0.8.orig.tar.gz
linux 77c61e0490ff7cf49aa8ffe62e117e85b3af08e14341c4b851f8d7ec757fe099 bzip2_1.0.8-5.1ubuntu0.1.debian.tar.bz2 $ubuntu/pool/main/b/bzip2/bzip2_1.0.8-5.1ubuntu0.1.debian.tar.bz2
linux 87d782671790af480ab3c3b258885ad2c239e41772e0116ceda6446c493f9dd4 bzip2_1.0.8-5.1ubuntu0.1.dsc $ubuntu/pool/main/b/bzip2/bzip2_1.0.8-5.1ubuntu0.1.dsc
linux 88525753f79d3bec27d2fa7c66aa0b92b3aa9498dafd93d7cfa4b3780cdae313 openssl_3.0.13.orig.tar.gz $ubuntu/pool/main/o/openssl/openssl_3.0.13.orig.tar.gz
linux a48bc18f3afd7030cd73712f180af343c4642e2d0ac65ac06fe1ec0ca7506077 openssl_3.0.13-0ubuntu3.15.debian.tar.xz $ubuntu/pool/main/o/openssl/openssl_3.0.13-0ubuntu3.15.debian.tar.xz
linux ae1c9a23219f0617b866d765f750ebd3bac74d93b7a235e6e53289781a056f0a openssl_3.0.13-0ubuntu3.15.dsc $ubuntu/pool/main/o/openssl/openssl_3.0.13-0ubuntu3.15.dsc
linux 99ee2ed8b98bcfad17bc57c2d9699d764f20fe29ad304c69b8eb28834ca3b48e freetype_2.13.2+dfsg.orig-ft2demos.tar.xz $ubuntu/pool/main/f/freetype/freetype_2.13.2+dfsg.orig-ft2demos.tar.xz
linux e58ba462f7bdcdc5899f777d33453c1ce6f6e46b080047580f45c9fd9f2dc08c freetype_2.13.2+dfsg.orig-ft2demos.tar.xz.asc $ubuntu/pool/main/f/freetype/freetype_2.13.2+dfsg.orig-ft2demos.tar.xz.asc
linux 685c25e1035a5076e5097186b3143b9c06878f3f9087d0a81e4d8538d5d15424 freetype_2.13.2+dfsg.orig-ft2docs.tar.xz $ubuntu/pool/main/f/freetype/freetype_2.13.2+dfsg.orig-ft2docs.tar.xz
linux d7e17c8a3bce50181530ebe06346f506cbfc92ecc5ca7cc395c7dbb24a71a5c0 freetype_2.13.2+dfsg.orig-ft2docs.tar.xz.asc $ubuntu/pool/main/f/freetype/freetype_2.13.2+dfsg.orig-ft2docs.tar.xz.asc
linux 48c78a4194adfcd15a4d089f3206dab8454c311f5577f3ef7eaef95f777f86e6 freetype_2.13.2+dfsg.orig.tar.xz $ubuntu/pool/main/f/freetype/freetype_2.13.2+dfsg.orig.tar.xz
linux 41371d9748c0e6f407c44c52c7fe5fbd4fbc2276a168ae528404731ae2e95b31 freetype_2.13.2+dfsg-1ubuntu0.1.debian.tar.xz $ubuntu/pool/main/f/freetype/freetype_2.13.2+dfsg-1ubuntu0.1.debian.tar.xz
linux 05368dead2fd8739fb2aa3a11e6ffd4376039b57536cc1c5cdadceb75496f385 freetype_2.13.2+dfsg-1ubuntu0.1.dsc $ubuntu/pool/main/f/freetype/freetype_2.13.2+dfsg-1ubuntu0.1.dsc
linux 2df939e37ac9c71ed8de3c1dd55641f069fd2ed1bfa162e2ce03c819084ddbd1 fribidi_1.0.13-3build1.dsc $ubuntu/pool/main/f/fribidi/fribidi_1.0.13-3build1.dsc
linux 7fa16c80c81bd622f7b198d31356da139cc318a63fc7761217af4130903f54a2 fribidi_1.0.13.orig.tar.xz $ubuntu/pool/main/f/fribidi/fribidi_1.0.13.orig.tar.xz
linux d6dd83149e92d0c335f56d5abebcad96591d291ad3d242efeeebcdab38dd20fc fribidi_1.0.13-3build1.debian.tar.xz $ubuntu/pool/main/f/fribidi/fribidi_1.0.13-3build1.debian.tar.xz
linux 38680f78a0ae6258826418cb5096c19ae3566ba8fee0a2112a0ec40056e58729 glib2.0_2.80.0.orig-unicode-data.tar.xz $ubuntu/pool/main/g/glib2.0/glib2.0_2.80.0.orig-unicode-data.tar.xz
linux 8228a92f92a412160b139ae68b6345bd28f24434a7b5af150ebe21ff587a561d glib2.0_2.80.0.orig.tar.xz $ubuntu/pool/main/g/glib2.0/glib2.0_2.80.0.orig.tar.xz
linux 3ee18682434b0213fbc7f5892527f748810a657aba2353d660c5eb30ed1656f0 glib2.0_2.80.0-6ubuntu3.8.debian.tar.xz $ubuntu/pool/main/g/glib2.0/glib2.0_2.80.0-6ubuntu3.8.debian.tar.xz
linux d44112b09956f61ffd5790a1a40e42558dca287052a945078cffff9b2490ee84 glib2.0_2.80.0-6ubuntu3.8.dsc $ubuntu/pool/main/g/glib2.0/glib2.0_2.80.0-6ubuntu3.8.dsc
linux 7a3b342c5681921ce2e0c2496509d30b5b078399d5a7bd2358f95166d57d91df graphite2_1.3.14.orig.tar.gz $ubuntu/pool/main/g/graphite2/graphite2_1.3.14.orig.tar.gz
linux 1a860887ab20f97acd841511d2318649cee3a31ad990a150f2999c36afa72693 graphite2_1.3.14-2ubuntu0.24.04.1.debian.tar.xz $ubuntu/pool/main/g/graphite2/graphite2_1.3.14-2ubuntu0.24.04.1.debian.tar.xz
linux c34a4358cdadcf881fe9794e13c27650f1c5de7bcd3aaf9a40a908047c4d44b4 graphite2_1.3.14-2ubuntu0.24.04.1.dsc $ubuntu/pool/main/g/graphite2/graphite2_1.3.14-2ubuntu0.24.04.1.dsc
linux ed14238fb748b9c822ebc80618df9e6018c3f5b919e4c11d6c4b965b90c9d6cb harfbuzz_8.3.0-2build2.dsc $ubuntu/pool/main/h/harfbuzz/harfbuzz_8.3.0-2build2.dsc
linux 109501eaeb8bde3eadb25fab4164e993fbace29c3d775bcaa1c1e58e2f15f847 harfbuzz_8.3.0.orig.tar.xz $ubuntu/pool/main/h/harfbuzz/harfbuzz_8.3.0.orig.tar.xz
linux cc8df8c8bf0301e1067c7a63a7f7384933935f13cdace4451aa3c8239fca40aa harfbuzz_8.3.0-2build2.debian.tar.xz $ubuntu/pool/main/h/harfbuzz/harfbuzz_8.3.0-2build2.debian.tar.xz
linux 28474ea6f6591c4d4cee972123587001a4e6e353412a41b3e9e82219818d5740 lcms2_2.14.orig.tar.gz $ubuntu/pool/main/l/lcms2/lcms2_2.14.orig.tar.gz
linux 3687da63f57e4133918208fe841e72e3a526de91bdd50e55b2f2d564b085abad lcms2_2.14-2ubuntu0.1.debian.tar.xz $ubuntu/pool/main/l/lcms2/lcms2_2.14-2ubuntu0.1.debian.tar.xz
linux 26e71fc172df784f47b04ce18e4783982d83e859ade9c60c08bed9e7c539347a lcms2_2.14-2ubuntu0.1.dsc $ubuntu/pool/main/l/lcms2/lcms2_2.14-2ubuntu0.1.dsc
linux c33b418e3b936ee3153de2c61cc638e7e4fe3156022a5c77d0711bcbb9d64f1f pcre2_10.42.orig.tar.gz $ubuntu/pool/main/p/pcre2/pcre2_10.42.orig.tar.gz
linux 29c5cb6ff392544bf48bd3ec2a98aa0da5297457fa4f4199a1c392ec3d41f19c pcre2_10.42-4ubuntu2.1.diff.gz $ubuntu/pool/main/p/pcre2/pcre2_10.42-4ubuntu2.1.diff.gz
linux 6272177be186d6f8ad16b668bb508b2e07645e05b5b8402d446492cb6d18104e pcre2_10.42-4ubuntu2.1.dsc $ubuntu/pool/main/p/pcre2/pcre2_10.42-4ubuntu2.1.dsc
linux 0dcf296eccbde692802d16cb51fcbd3bc386e76a069609fd36f319b399492796 libplacebo_6.338.2-2build1.dsc $ubuntu/pool/universe/libp/libplacebo/libplacebo_6.338.2-2build1.dsc
linux 15e30f2fdce41ac1cd6475763dbf6a6011126bc7a4f901b1dcfaa4582b01ec30 libplacebo_6.338.2.orig.tar.xz $ubuntu/pool/universe/libp/libplacebo/libplacebo_6.338.2.orig.tar.xz
linux 02b192d87a8f3090d04e4b2683761d8cd4a80b433188018e1033df901ddfe9a9 libplacebo_6.338.2-2build1.debian.tar.xz $ubuntu/pool/universe/libp/libplacebo/libplacebo_6.338.2-2build1.debian.tar.xz
linux fecc95b46cf05e8e3fc8a414750e0ba5aad00d89e9fdf175e94ff041caf1a03a libpng1.6_1.6.43.orig.tar.gz $ubuntu/pool/main/libp/libpng1.6/libpng1.6_1.6.43.orig.tar.gz
linux 2410934848547a4c826e102b54e2abdf005fa4e0fbc4aa1e9fe3200638cd9328 libpng1.6_1.6.43-5ubuntu0.6.debian.tar.xz $ubuntu/pool/main/libp/libpng1.6/libpng1.6_1.6.43-5ubuntu0.6.debian.tar.xz
linux b5c3539ad4f9289480671a5a89aad2d9ad60c5eae79255460f1e929be1e272ad libpng1.6_1.6.43-5ubuntu0.6.dsc $ubuntu/pool/main/libp/libpng1.6/libpng1.6_1.6.43-5ubuntu0.6.dsc
linux bb228cdb497d198cef494256d0069a43734ca70ec1137ccebeafba568a501465 libunibreak_5.1-2build1.dsc $ubuntu/pool/universe/libu/libunibreak/libunibreak_5.1-2build1.dsc
linux d59878d82c84a60b592940a8e1a264f0a9714f0a9a8868b099846f780d9dc167 libunibreak_5.1.orig.tar.gz $ubuntu/pool/universe/libu/libunibreak/libunibreak_5.1.orig.tar.gz
linux 20e1bc390e2464ff3df2dbcdc73c7359346239599ec3ce734b7d49c6da4cdfd2 libunibreak_5.1-2build1.debian.tar.xz $ubuntu/pool/universe/libu/libunibreak/libunibreak_5.1-2build1.debian.tar.xz
linux a19cbc3b537cd04f43a4511448bcd9fd0b065ea9a3f83efd590bac0a567b0752 libva_2.20.0.orig.tar.xz $ubuntu/pool/universe/libv/libva/libva_2.20.0.orig.tar.xz
linux b2173dfe95da760ec5633bf8c673bd305d5a69b2f0b1b6d6c21966cae58b06d3 libva_2.20.0-2ubuntu0.2.debian.tar.xz $ubuntu/pool/universe/libv/libva/libva_2.20.0-2ubuntu0.2.debian.tar.xz
linux d00d24e6d37e9eceb2bd6965164cdb8e1c9104723ea9548e2232fafb3e5dd617 libva_2.20.0-2ubuntu0.2.dsc $ubuntu/pool/universe/libv/libva/libva_2.20.0-2ubuntu0.2.dsc
linux aa994469e76b17847c1153d43cd37884adebbce5008ad221ef0e1e8ad840f8de libxcb_1.15-1ubuntu2.dsc $ubuntu/pool/main/libx/libxcb/libxcb_1.15-1ubuntu2.dsc
linux 1cb65df8543a69ec0555ac696123ee386321dfac1964a3da39976c9a05ad724d libxcb_1.15.orig.tar.gz $ubuntu/pool/main/libx/libxcb/libxcb_1.15.orig.tar.gz
linux d45e55f604af83b47f621c97b49885f35b518909bb3b9f9c877cfd3daddfb5ee libxcb_1.15-1ubuntu2.diff.gz $ubuntu/pool/main/libx/libxcb/libxcb_1.15-1ubuntu2.diff.gz
linux 0d2faa231cb2d5750994e8d93c4973cf4c24f74d8f1912234a1a244952d5e627 libxext_1.3.4-1build2.dsc $ubuntu/pool/main/libx/libxext/libxext_1.3.4-1build2.dsc
linux 8ef0789f282826661ff40a8eef22430378516ac580167da35cc948be9041aac1 libxext_1.3.4.orig.tar.gz $ubuntu/pool/main/libx/libxext/libxext_1.3.4.orig.tar.gz
linux ed434b3facecdf2c1f9ff58578033da61e087d73d6ce2d84433871ee4fb22ba6 libxext_1.3.4-1build2.diff.gz $ubuntu/pool/main/libx/libxext/libxext_1.3.4-1build2.diff.gz
linux 85c831269a0513da54f1cd6a816a9d2fa75d4bb04159079618fb2ecc5ea2b7ab libxfixes_6.0.0-2build1.dsc $ubuntu/pool/main/libx/libxfixes/libxfixes_6.0.0-2build1.dsc
linux 82045da5625350838390c9440598b90d69c882c324ca92f73af9f0e992cb57c7 libxfixes_6.0.0.orig.tar.gz $ubuntu/pool/main/libx/libxfixes/libxfixes_6.0.0.orig.tar.gz
linux e5598f42aa32140936c7772ab4d99ba35ecb859c29adc1703bb51440e7a54928 libxfixes_6.0.0.orig.tar.gz.asc $ubuntu/pool/main/libx/libxfixes/libxfixes_6.0.0.orig.tar.gz.asc
linux 4afbdb618c6f60e95c4251a8c08e928b19c3a19316dc9801c1e51c5c0b7f7a49 libxfixes_6.0.0-2build1.diff.gz $ubuntu/pool/main/libx/libxfixes/libxfixes_6.0.0-2build1.diff.gz
linux 411d02e86fec75d9d9cc7446a0547fb5abd7044afbcf183a37f72128fdb8dd06 libxpresent_1.0.0-2build2.dsc $ubuntu/pool/universe/libx/libxpresent/libxpresent_1.0.0-2build2.dsc
linux 92f1bdfb67ae2ffcdb25ad72c02cac5e4912dc9bc792858240df1d7f105946fa libxpresent_1.0.0.orig.tar.gz $ubuntu/pool/universe/libx/libxpresent/libxpresent_1.0.0.orig.tar.gz
linux f7608090926d141bb6399567ad9c75378a12064ae9b5e094dbed9d58d6524e50 libxpresent_1.0.0-2build2.diff.gz $ubuntu/pool/universe/libx/libxpresent/libxpresent_1.0.0-2build2.diff.gz
linux 733b5be4c178c017181334e36d6649455c78222b2a146bb732a03598f8da6ffc libxrandr_1.5.2-2build1.dsc $ubuntu/pool/main/libx/libxrandr/libxrandr_1.5.2-2build1.dsc
linux 3f10813ab355e7a09f17e147d61b0ce090d898a5ea5b5519acd0ef68675dcf8e libxrandr_1.5.2.orig.tar.gz $ubuntu/pool/main/libx/libxrandr/libxrandr_1.5.2.orig.tar.gz
linux 65e365ff8a3205d5532e9867045238dc6abbcdc146e063bafc7f614e730c1b7b libxrandr_1.5.2-2build1.diff.gz $ubuntu/pool/main/libx/libxrandr/libxrandr_1.5.2-2build1.diff.gz
linux fbc2eb120a290e733e01420e6bed5f7158a822e94baa8952b10bdcf5ae55a15e libxrender_0.9.10-1.1build1.dsc $ubuntu/pool/main/libx/libxrender/libxrender_0.9.10-1.1build1.dsc
linux 770527cce42500790433df84ec3521e8bf095dfe5079454a92236494ab296adf libxrender_0.9.10.orig.tar.gz $ubuntu/pool/main/libx/libxrender/libxrender_0.9.10.orig.tar.gz
linux 7a09639402d326f5c665c848286d26932ba3a1d104e41fb921500ca93895598b libxrender_0.9.10-1.1build1.diff.gz $ubuntu/pool/main/libx/libxrender/libxrender_0.9.10-1.1build1.diff.gz
linux 55513f76c0237edbd32ff2abd9584d7f5c665701ce923ec9c57bd0bb30cd8502 libxss_1.2.3-1build3.dsc $ubuntu/pool/main/libx/libxss/libxss_1.2.3-1build3.dsc
linux 4f74e7e412144591d8e0616db27f433cfc9f45aae6669c6c4bb03e6bf9be809a libxss_1.2.3.orig.tar.gz $ubuntu/pool/main/libx/libxss/libxss_1.2.3.orig.tar.gz
linux 4e900524d56c8e7263365267efa91bb3671110c9eb28ccab58f70e2188f0b91b libxss_1.2.3.orig.tar.gz.asc $ubuntu/pool/main/libx/libxss/libxss_1.2.3.orig.tar.gz.asc
linux 2bf37e033f8f4e743cce4c32db62b5e5974da7e48c77a891df74a5f302c2342d libxss_1.2.3-1build3.diff.gz $ubuntu/pool/main/libx/libxss/libxss_1.2.3-1build3.diff.gz
linux 5eea0322c1c21c75cad3b607ac1c43ff5c71e014b8ac4a34300b5e2b80d02e70 zlib_1.3.dfsg.orig.tar.xz $ubuntu/pool/main/z/zlib/zlib_1.3.dfsg.orig.tar.xz
linux de1c2d532ecb7caf99ccb905fb03c37fe0c8117fbdc79d05be4474b71e6398c2 zlib_1.3.dfsg-3.1ubuntu2.2.debian.tar.xz $ubuntu/pool/main/z/zlib/zlib_1.3.dfsg-3.1ubuntu2.2.debian.tar.xz
linux 56a7cd029863cbddafce831e4e63cccfa84017f93617929cb7c02f80bb5a122a zlib_1.3.dfsg-3.1ubuntu2.2.dsc $ubuntu/pool/main/z/zlib/zlib_1.3.dfsg-3.1ubuntu2.2.dsc
EOF
}

manifest() {
  upstream_manifest
  if $macos; then macos_manifest; else desktop_manifest; fi
}

rm -rf "$stage"
mkdir -p "$cache" "$stage"
count=0
while read -r dir sum file url; do
  [ -n "$dir" ] || continue
  if [ ! -f "$cache/$file" ] || ! echo "$sum  $cache/$file" | sha256sum --check --status; then
    echo "fetching $file"
    curl --fail --silent --show-error --location --retry 3 --max-time 1800 -o "$cache/$file.part" "$url"
    mv "$cache/$file.part" "$cache/$file"
  fi
  if ! echo "$sum  $cache/$file" | sha256sum --check --status; then
    echo "SHA-256 mismatch for $file from $url" >&2
    exit 1
  fi
  mkdir -p "$stage/$dir"
  ln -f "$cache/$file" "$stage/$dir/$file" 2>/dev/null || cp "$cache/$file" "$stage/$dir/$file"
  count=$((count + 1))
done < <(manifest)

manifest | awk '{ print $2 "  " $1 "/" $3 }' > "$stage/SHA256SUMS"

if $macos; then
  # A copy of each component's licence texts, taken from its own source archive.
  while read -r dir sum file url; do
    case "$file" in *.tar.gz | *.tgz | *.tar.xz | *.tar.bz2) ;; *) continue ;; esac
    members=$(tar -tf "$cache/$file" | grep -iE \
      '^[^/]+/(copying[^/]*|licen[cs]e[^/]*|copyright[^/]*|notice[^/]*|licenses/[^/]+|docs/(ftl|gplv2)\.txt|gettext-runtime/intl/copying[^/]*)$' || true)
    [ -n "$members" ] || continue
    dest="$stage/licenses/${file%.t*}"
    mkdir -p "$dest"
    echo "$members" | tar -xf "$cache/$file" -C "$dest" --strip-components=1 --files-from=-
  done < <(manifest)

  cat > "$stage/README.md" <<'EOF'
# Corresponding source for the mpv runtime in TMPlayer's macOS build

TMPlayer's macOS (Apple Silicon) build plays video through mediamp 0.5.0 (`org.openani.mediamp`,
by Open Ani, Apache-2.0), whose runtime jar `mediamp-mpv-runtime-macos-arm64` bundles libmpv,
FFmpeg and the dylibs listed below. This archive holds the exact source of each of them.
`SHA256SUMS` lists every file; `licenses/` holds a copy of each component's licence texts, taken
from its source archive.

TMPlayer's own source is at <https://github.com/dracu-lah/TMPlayer>, under GPL-3.0-or-later.

## Built from source by mediamp (`upstream/`)

| Component | Version | Licence | File |
|---|---|---|---|
| mediamp (the build scripts in `buildSrc/`, the JNI glue `libmediampv.dylib` with its Metal/IOSurface renderer, and `mediamp-mpv/render_d3d11.patch`, which is applied to the mpv tree on every platform) | 0.5.0 | Apache-2.0 | `mediamp-0.5.0.tar.gz` |
| mpv (`libmpv.2.dylib`) | 0.41.0, commit `41f6a645` | LGPL-2.1-or-later (`-Dgpl=false`) | `mpv-0.41.0.tar.gz` |
| FFmpeg (`libavutil.60`, `libavcodec.62`, `libavformat.62`, `libavfilter.11`, `libavdevice.62`, `libswresample.6`, `libswscale.9`) | release/8.0 branch, commit `449453a9` (reports 8.0.1) | LGPL-2.1-or-later (no `--enable-gpl`, no non-free parts) | `ffmpeg-8.0.1-449453a9.tar.gz` |
| dav1d (linked statically into libavcodec) | 1.5.4, commit `54706fc6` | BSD-2-Clause | `dav1d-1.5.4.tar.gz` |

mediamp builds these on its Apple Silicon runner for macOS 12.0 and later. The FFmpeg configure
line (`buildSrc/src/main/kotlin/ffmpeg/FfmpegTargets.kt`, macOS target: Secure Transport for TLS,
VideoToolbox, dav1d) is embedded in the shipped `libavutil`, and the mpv meson options
(`buildSrc/src/main/kotlin/mpv/MpvTargets.kt`, macOS target: Cocoa, Core Audio, OpenGL,
VideoToolbox, Swift) in the shipped `libmpv`.

## Homebrew libraries (`macos/`)

mediamp's release job installs `libass` and `libplacebo` from Homebrew and copies every non-system
dylib they need into the runtime, rewriting the install names to `@loader_path` and signing them
ad hoc. Each dylib below has the same LC_UUID and the same code (`__TEXT,__text`) as the dylib in
the Homebrew `arm64_sequoia` bottle of the version listed. `macos/homebrew/` holds each formula
file as of the homebrew-core commit that published that bottle (the bottle's SHA-256 is in it):
the build recipe, configure options and patches. The source archives are the ones the formulae
name, with the SHA-256 they pin.

| Library (dylib) | Homebrew formula and version | Licence | Source |
|---|---|---|---|
| libass (`libass.9.dylib`) | libass 0.17.4 | ISC | `libass-0.17.4.tar.xz` |
| libplacebo (`libplacebo.351.dylib`) | libplacebo 7.351.0 (bottle rebuild 1) | LGPL-2.1-or-later | `libplacebo-v7.351.0.tar.bz2`, plus the build-time generators glad2 2.0.8 (MIT), Jinja2 3.1.6 and MarkupSafe 3.0.2 (BSD-3-Clause) |
| shaderc (`libshaderc_shared.1.dylib`) | shaderc 2025.3 | Apache-2.0 | `shaderc-2025.3.tar.gz` |
| glslang (static, inside shaderc) | 15.3.0, commit `efd24d75` | BSD-3-Clause and others, see its LICENSE.txt | `glslang-15.3.0-efd24d75.tar.gz` |
| SPIRV-Tools (static, inside shaderc) | commit `33e02568` | Apache-2.0 | `SPIRV-Tools-33e02568.tar.gz` |
| SPIRV-Headers (headers, inside shaderc) | commit `2a611a97` | MIT | `SPIRV-Headers-2a611a97.tar.gz` |
| Vulkan loader (`libvulkan.1.4.326.dylib`) | vulkan-loader 1.4.326 | Apache-2.0 | `vulkan-loader-1.4.326.tar.gz` |
| Little CMS (`liblcms2.2.dylib`) | little-cms2 2.17 | MIT | `lcms2-2.17.tar.gz` |
| FreeType (`libfreetype.6.dylib`) | freetype 2.13.3 | FTL or GPL-2.0-or-later | `freetype-2.13.3.tar.xz` |
| FriBidi (`libfribidi.0.dylib`) | fribidi 1.0.16 | LGPL-2.1-or-later | `fribidi-1.0.16.tar.xz` |
| HarfBuzz (`libharfbuzz.0.dylib`) | harfbuzz 11.4.5 | MIT | `harfbuzz-11.4.5.tar.gz` |
| Graphite2 (`libgraphite2.3.2.1.dylib`) | graphite2 1.3.14 | LGPL-2.1-or-later, GPL-2.0-or-later or MPL-1.1, at your choice | `graphite2-1.3.14.tgz` |
| libunibreak (`libunibreak.6.dylib`) | libunibreak 6.1 | Zlib | `libunibreak-6.1.tar.gz` |
| libpng (`libpng16.16.dylib`) | libpng 1.6.50 | libpng-2.0 | `libpng-1.6.50.tar.xz` |
| GLib (`libglib-2.0.0.dylib`) | glib 2.84.4, with Homebrew's `glib-hardcoded-paths.diff` | LGPL-2.1-or-later | `glib-2.84.4.tar.xz` |
| PCRE2 (`libpcre2-8.0.dylib`) | pcre2 10.46 | BSD-3-Clause | `pcre2-10.46.tar.bz2` |
| GNU libintl, from gettext (`libintl.8.dylib`) | gettext 0.26 | LGPL-2.1-or-later | `gettext-0.26.tar.gz` |

zlib, bzip2, libiconv, libc++ and the system frameworks are part of macOS and are not shipped.

libplacebo also compiles in two header-only build dependencies that Homebrew does not pin to a
version: fast_float (MIT, Apache-2.0 or BSL-1.0) and the Vulkan headers (Apache-2.0 or MIT). Their
versions cannot be read from the shipped dylib, so they are not included here; both are
permissively licensed and available from their upstream projects.
EOF
else
cat > "$stage/README.md" <<'EOF'
# Corresponding source for the mpv runtime in TMPlayer's desktop builds

TMPlayer's Windows and Linux builds play video through mediamp 0.5.0 (`org.openani.mediamp`, by
Open Ani, Apache-2.0), whose runtime jars `mediamp-mpv-runtime-windows-x64` and
`mediamp-mpv-runtime-linux-x64` bundle libmpv, FFmpeg and the shared libraries listed below. This
archive holds the exact source of each of them. `SHA256SUMS` lists every file.

TMPlayer's own source is at <https://github.com/dracu-lah/TMPlayer>, under GPL-3.0-or-later.

## Built from source by mediamp (`upstream/`)

| Component | Version | Licence | File |
|---|---|---|---|
| mediamp (the build scripts in `buildSrc/`, the JNI glue `libmediampv`/`mediampv.dll`, and `mediamp-mpv/render_d3d11.patch`, applied to mpv on Windows) | 0.5.0 | Apache-2.0 | `mediamp-0.5.0.tar.gz` |
| mpv (libmpv) | 0.41.0, commit `41f6a645` | Linux build: GPL-2.0-or-later (the `gpl` feature is on). Windows build: LGPL-2.1-or-later (`-Dgpl=false`) | `mpv-0.41.0.tar.gz` |
| FFmpeg | release/8.0 branch, commit `449453a9` (reports 8.0.1) | LGPL-2.1-or-later (no `--enable-gpl`, no non-free parts) | `ffmpeg-8.0.1-449453a9.tar.gz` |
| dav1d (linked statically into libavcodec) | 1.5.4, commit `54706fc6` | BSD-2-Clause | `dav1d-1.5.4.tar.gz` |

The FFmpeg configure line and the mpv meson options are generated by mediamp's `buildSrc`
(`mpv/MpvSupport.kt`, `ffmpeg/`); the configure line is also embedded in each shipped
`libavutil`. The mediamp and mpv tarballs are GitHub archives of the tag and commit above; mediamp's
git submodules (mpv, FFmpeg, dav1d) are the three other archives.

## Windows x64 (`windows/`)

MSYS2 UCRT64 source packages: each holds the PKGBUILD, MSYS2's patches and the upstream source.
Every DLL in the runtime jar is byte for byte the one in the binary package of the same version.

| Library (DLL) | Package version | Licence |
|---|---|---|
| libass (`libass-9.dll`) | 0.17.5-1 | ISC |
| libplacebo (`libplacebo-360.dll`) | 7.360.1-2 | LGPL-2.1-or-later |
| shaderc (`libshaderc_shared.dll`) | 2026.3-1 | Apache-2.0 |
| glslang (static, inside shaderc) | 16.3.0-1 | BSD-3-Clause and others, see its LICENSE.txt |
| SPIRV-Tools (static, inside shaderc) | 1.4.357.0-1 | Apache-2.0 |
| SPIRV-Cross (`libspirv-cross-c-shared.dll`) | 1.4.357.0-1 | Apache-2.0 |
| Vulkan loader (`vulkan-1.dll`) | 1.4.357.0-1 | Apache-2.0 |
| libdovi (`libdovi.dll`) | 3.4.0-1 | MIT |
| Little CMS (`liblcms2-2.dll`) | 2.19.1-1 | MIT |
| FreeType (`libfreetype-6.dll`) | 2.14.3-1 | FTL or GPL-2.0-or-later |
| FriBidi (`libfribidi-0.dll`) | 1.0.16-1 | LGPL-2.1-or-later |
| HarfBuzz (`libharfbuzz-0.dll`) | 14.4.0-1 | MIT |
| Graphite2 (`libgraphite2.dll`) | 1.3.15-1 | LGPL-2.1-or-later |
| libunibreak (`libunibreak-7.dll`) | 7.0-1 | Zlib |
| Fontconfig (`libfontconfig-1.dll`) | 2.18.3-1 | HPND-style Fontconfig licence |
| Expat (`libexpat-1.dll`) | 2.8.4-2 | MIT |
| libpng (`libpng16-16.dll`) | 1.6.58-1 | libpng-2.0 |
| zlib (`zlib1.dll`) | 1.3.2-2 | Zlib |
| bzip2 (`libbz2-1.dll`) | 1.0.8-4 | bzip2-1.0.6 |
| Brotli (`libbrotlicommon.dll`, `libbrotlidec.dll`) | 1.2.0-1 | MIT |
| GLib (`libglib-2.0-0.dll`) | 2.90.0-1 | LGPL-2.1-or-later |
| PCRE2 (`libpcre2-8-0.dll`) | 10.48-3 | BSD-3-Clause |
| GNU libintl, from gettext (`libintl-8.dll`) | 1.0-1 | LGPL-2.1-or-later |
| GNU libiconv (`libiconv-2.dll`) | 1.19-1 | LGPL-2.1-or-later |
| OpenSSL (`libcrypto-3-x64.dll`, `libssl-3-x64.dll`) | 3.6.4-1 | Apache-2.0 |
| GCC runtime (`libgcc_s_seh-1.dll`, `libstdc++-6.dll`) | gcc 16.2.0-3 | GPL-3.0-or-later WITH GCC-exception-3.1 |
| winpthreads (`libwinpthread-1.dll`) | 14.0.0.r375.g9c1abbbf5-1 | MIT and BSD-3-Clause-Clear |

## Linux x64 (`linux/`)

Ubuntu 24.04 source packages (`.dsc`, upstream `.orig` tarball and Ubuntu's `.debian` changes;
unpack with `dpkg-source -x <name>.dsc`). Each library's GNU build ID matches the Ubuntu binary
package of the same version.

| Library | Source package version | Licence |
|---|---|---|
| libass (`libass.so.9`) | libass 1:0.17.1-2build1 | ISC |
| libplacebo (`libplacebo.so.338`) | libplacebo 6.338.2-2build1 | LGPL-2.1-or-later |
| Little CMS (`liblcms2.so.2`) | lcms2 2.14-2ubuntu0.1 | MIT |
| FreeType (`libfreetype.so.6`) | freetype 2.13.2+dfsg-1ubuntu0.1 | FTL or GPL-2.0-or-later |
| FriBidi (`libfribidi.so.0`) | fribidi 1.0.13-3build1 | LGPL-2.1-or-later |
| HarfBuzz (`libharfbuzz.so.0`) | harfbuzz 8.3.0-2build2 | MIT |
| Graphite2 (`libgraphite2.so.3`) | graphite2 1.3.14-2ubuntu0.24.04.1 | LGPL-2.1-or-later |
| libunibreak (`libunibreak.so.5`) | libunibreak 5.1-2build1 | Zlib |
| libpng (`libpng16.so.16`) | libpng1.6 1.6.43-5ubuntu0.6 | libpng-2.0 |
| zlib (`libz.so.1`) | zlib 1:1.3.dfsg-3.1ubuntu2.2 | Zlib |
| bzip2 (`libbz2.so.1.0`) | bzip2 1.0.8-5.1ubuntu0.1 | bzip2-1.0.6 |
| Brotli (`libbrotlicommon.so.1`, `libbrotlidec.so.1`) | brotli 1.1.0-2build2 | MIT |
| GLib (`libglib-2.0.so.0`) | glib2.0 2.80.0-6ubuntu3.8 | LGPL-2.1-or-later |
| PCRE2 (`libpcre2-8.so.0`) | pcre2 10.42-4ubuntu2.1 | BSD-3-Clause |
| OpenSSL (`libcrypto.so.3`, `libssl.so.3`) | openssl 3.0.13-0ubuntu3.15 | Apache-2.0 |
| libva (`libva.so.2`, `libva-drm.so.2`, `libva-x11.so.2`) | libva 2.20.0-2ubuntu0.2 | MIT |
| libxcb (`libxcb-dri3.so.0`) | libxcb 1.15-1ubuntu2 | MIT |
| libXext (`libXext.so.6`) | libxext 2:1.3.4-1build2 | MIT |
| libXfixes (`libXfixes.so.3`) | libxfixes 1:6.0.0-2build1 | MIT |
| libXpresent (`libXpresent.so.1`) | libxpresent 1.0.0-2build2 | MIT |
| libXrandr (`libXrandr.so.2`) | libxrandr 2:1.5.2-2build1 | MIT |
| libXrender (`libXrender.so.1`) | libxrender 1:0.9.10-1.1build1 | MIT |
| libXss (`libXss.so.1`) | libxss 1:1.2.3-1build3 | MIT |

On Linux the FFmpeg build also compiles in the NVIDIA codec headers (nv-codec-headers, MIT,
headers only) from Ubuntu's `libffmpeg-nvenc-dev`; no library of theirs is shipped.
EOF
fi

tar -C "$out" --owner=0 --group=0 --sort=name --mtime='2026-09-19 00:00Z' -chf - "$NAME" \
  | gzip -n > "$out/$NAME.tar.gz"
echo "wrote $out/$NAME.tar.gz ($count files, $(du -h "$out/$NAME.tar.gz" | cut -f1))"
sha256sum "$out/$NAME.tar.gz"
