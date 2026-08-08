(function () {
  'use strict';

  const youtubeMode   = document.getElementById('youtubeMode');
  const nativeMode    = document.getElementById('nativeMode');
  const ytFrame       = document.getElementById('ytFrame');
  const ytSearch      = document.getElementById('ytSearch');
  const video         = document.getElementById('video');
  const controls      = document.getElementById('controls');
  const buffering     = document.getElementById('buffering');
  const statusMsg     = document.getElementById('statusMsg');
  const netSpeedEl    = document.getElementById('netSpeed');
  const seekBar       = document.getElementById('seekBar');
  const currentTimeEl = document.getElementById('currentTime');
  const durationEl    = document.getElementById('duration');
  const btnPlayPause  = document.getElementById('btnPlayPause');
  const btnSpeed      = document.getElementById('btnSpeed');
  const btnLock       = document.getElementById('btnLock');

  let hls = null;
  let isLocked = false;
  let controlsTimer = null;

  function formatTime(sec) {
    if (!isFinite(sec) || sec < 0) return '00:00';
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60);
    return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
  }

  function showStatus(msg, duration = 3000) {
    statusMsg.textContent = msg;
    statusMsg.classList.remove('hidden');
    setTimeout(() => statusMsg.classList.add('hidden'), duration);
  }

  function extractYouTubeId(url) {
    const reg = /(?:youtube\.com\/(?:[^\/]+\/.+\/|(?:v|e(?:mbed)?)\/|.*[?&]v=)|youtu\.be\/)([^"&?\/\s]{11})/i;
    const match = url.match(reg);
    return match ? match[1] : null;
  }

  // 搜尋或播放 YouTube
  function playYouTube(query) {
    if (!query) return;
    const id = extractYouTubeId(query);
    if (id) {
      ytFrame.src = `https://www.youtube-nocookie.com/embed/${id}?autoplay=1&rel=0&modestbranding=1&playsinline=1`;
    } else {
      // 當作搜尋關鍵字
      ytFrame.src = `https://www.youtube-nocookie.com/embed?listType=search&list=${encodeURIComponent(query)}&autoplay=0&rel=0&modestbranding=1&playsinline=1`;
    }
  }

  // 播放自己的影片（MP4 / HLS）
  function playNative(url) {
    youtubeMode.classList.add('hidden');
    nativeMode.classList.remove('hidden');

    if (hls) { hls.destroy(); hls = null; }
    video.src = '';

    const isHls = /\.m3u8($|\?)/i.test(url) || url.includes('m3u8');

    if (isHls && window.Hls && Hls.isSupported()) {
      hls = new Hls({
        enableWorker: true,
        maxBufferLength: 40,
        maxMaxBufferLength: 60
      });
      hls.loadSource(url);
      hls.attachMedia(video);
      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        video.play().catch(() => showStatus('請點擊播放'));
      });
    } else {
      video.src = url;
      video.play().catch(() => showStatus('請點擊播放'));
    }
  }

  // 網路搜尋
  ytSearch.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      playYouTube(ytSearch.value.trim());
    }
  });

  // 切換到自己的播放器
  document.getElementById('btnSwitchPlayer').addEventListener('click', () => {
    const url = prompt('請輸入 MP4 或 HLS (m3u8) 網址：');
    if (url && url.startsWith('http')) {
      playNative(url.trim());
    }
  });

  // 返回 YouTube
  document.getElementById('btnBackToYt').addEventListener('click', () => {
    if (hls) { hls.destroy(); hls = null; }
    video.pause();
    video.src = '';
    nativeMode.classList.add('hidden');
    youtubeMode.classList.remove('hidden');
  });

  // 原生播放器控制
  btnPlayPause.addEventListener('click', () => {
    if (video.paused) {
      video.play();
      btnPlayPause.textContent = '⏸';
    } else {
      video.pause();
      btnPlayPause.textContent = '▶';
    }
  });

  document.getElementById('btnRewind').addEventListener('click', () => {
    video.currentTime = Math.max(0, video.currentTime - 10);
  });

  document.getElementById('btnForward').addEventListener('click', () => {
    video.currentTime = Math.min(video.duration || 0, video.currentTime + 10);
  });

  btnSpeed.addEventListener('click', () => {
    const speeds = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0];
    let idx = speeds.indexOf(video.playbackRate);
    idx = (idx + 1) % speeds.length;
    video.playbackRate = speeds[idx];
    btnSpeed.textContent = speeds[idx] + 'x';
  });

  btnLock.addEventListener('click', () => {
    isLocked = !isLocked;
    btnLock.textContent = isLocked ? '解鎖' : '鎖定';
    nativeMode.classList.toggle('locked', isLocked);
  });

  document.getElementById('btnFullscreen').addEventListener('click', () => {
    const el = document.querySelector('.player-wrapper');
    if (!document.fullscreenElement) {
      (el.requestFullscreen || el.webkitRequestFullscreen)?.call(el);
    } else {
      (document.exitFullscreen || document.webkitExitFullscreen)?.call(document);
    }
  });

  video.addEventListener('timeupdate', () => {
    if (!video.duration) return;
    currentTimeEl.textContent = formatTime(video.currentTime);
    durationEl.textContent = formatTime(video.duration);
    if (!seekBar.matches(':active')) {
      seekBar.value = (video.currentTime / video.duration) * 1000;
    }
  });

  seekBar.addEventListener('input', () => {
    if (video.duration) {
      video.currentTime = (seekBar.value / 1000) * video.duration;
    }
  });

  video.addEventListener('waiting', () => buffering.classList.remove('hidden'));
  video.addEventListener('playing', () => {
    buffering.classList.add('hidden');
    btnPlayPause.textContent = '⏸';
  });
  video.addEventListener('pause', () => btnPlayPause.textContent = '▶');

  // 網路速度顯示
  function updateNet() {
    if (navigator.connection && navigator.connection.downlink) {
      netSpeedEl.textContent = (navigator.connection.downlink * 1000).toFixed(0) + ' kbps';
    }
  }
  setInterval(updateNet, 3000);
  updateNet();

})();
