(function () {
  'use strict';

  const homeScreen   = document.getElementById('homeScreen');
  const playerScreen = document.getElementById('playerScreen');
  const video        = document.getElementById('video');
  const controls     = document.getElementById('controls');
  const buffering    = document.getElementById('buffering');
  const statusMsg    = document.getElementById('statusMsg');
  const netSpeedEl   = document.getElementById('netSpeed');
  const networkStatus= document.getElementById('networkStatus');
  const seekBar      = document.getElementById('seekBar');
  const currentTimeEl= document.getElementById('currentTime');
  const durationEl   = document.getElementById('duration');
  const btnPlayPause = document.getElementById('btnPlayPause');
  const btnSpeed     = document.getElementById('btnSpeed');
  const btnLock      = document.getElementById('btnLock');

  let hls = null;
  let currentUrl = '';
  let isLocked = false;
  let controlsTimer = null;
  let lastNetworkOnline = navigator.onLine;
  let stallCount = 0;

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

  function savePosition() {
    if (!currentUrl || !video.duration) return;
    try {
      const key = 'sp_pos_' + btoa(currentUrl).slice(0, 40);
      localStorage.setItem(key, JSON.stringify({
        pos: video.currentTime,
        dur: video.duration,
        ts: Date.now()
      }));
    } catch (e) {}
  }

  function loadPosition(url) {
    try {
      const key = 'sp_pos_' + btoa(url).slice(0, 40);
      const data = JSON.parse(localStorage.getItem(key) || 'null');
      if (data && data.pos > 5 && Date.now() - data.ts < 7 * 24 * 3600 * 1000) {
        return data.pos;
      }
    } catch (e) {}
    return 0;
  }

  function updateNetworkStatus() {
    const online = navigator.onLine;
    let type = '未知';
    if (navigator.connection) {
      const c = navigator.connection;
      type = c.effectiveType || c.type || '未知';
      const down = c.downlink ? (c.downlink * 1000).toFixed(0) + ' kbps' : '';
      networkStatus.textContent = online
        ? `網路：${type} ${down ? '· ' + down : ''} · 已連線`
        : '網路：已斷線';
      if (netSpeedEl) netSpeedEl.textContent = down || '';
    } else {
      networkStatus.textContent = online ? '網路：已連線' : '網路：已斷線';
    }

    if (!online && lastNetworkOnline) {
      savePosition();
      video.pause();
      showStatus('網路已斷線，已保留播放位置');
    } else if (online && !lastNetworkOnline) {
      showStatus('網路已恢復，正在恢復播放…');
      if (currentUrl) {
        setTimeout(() => {
          if (video.paused) video.play().catch(() => {});
        }, 600);
      }
    }
    lastNetworkOnline = online;
  }

  window.addEventListener('online', updateNetworkStatus);
  window.addEventListener('offline', updateNetworkStatus);
  if (navigator.connection) {
    navigator.connection.addEventListener('change', updateNetworkStatus);
  }
  setInterval(updateNetworkStatus, 4000);
  updateNetworkStatus();

  function checkStall() {
    if (video.paused || video.ended) return;
    if (video.readyState < 3 && !video.paused) {
      stallCount++;
      if (stallCount >= 3) {
        showStatus('偵測到卡頓，嘗試降低負載…');
        stallCount = 0;
      }
    } else {
      stallCount = 0;
    }
  }
  setInterval(checkStall, 1000);

  function playUrl(url) {
    if (!url) return;
    currentUrl = url.trim();
    homeScreen.classList.add('hidden');
    playerScreen.classList.remove('hidden');

    if (hls) {
      hls.destroy();
      hls = null;
    }
    video.src = '';
    video.load();

    const isHls = /\.m3u8($|\?)/i.test(currentUrl) || currentUrl.includes('m3u8');

    if (isHls && Hls.isSupported()) {
      hls = new Hls({
        enableWorker: true,
        lowLatencyMode: false,
        backBufferLength: 30,
        maxBufferLength: 40,
        maxMaxBufferLength: 60,
        startLevel: -1,
        abrEwmaDefaultEstimate: 500000
      });
      hls.loadSource(currentUrl);
      hls.attachMedia(video);
      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        const pos = loadPosition(currentUrl);
        if (pos > 0) video.currentTime = pos;
        video.play().catch(() => showStatus('請點擊播放按鈕'));
      });
      hls.on(Hls.Events.ERROR, (_, data) => {
        if (data.fatal) {
          showStatus('播放錯誤，嘗試恢復…');
          if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
            hls.startLoad();
          } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
            hls.recoverMediaError();
          }
        }
      });
    } else {
      video.src = currentUrl;
      video.addEventListener('loadedmetadata', function onMeta() {
        const pos = loadPosition(currentUrl);
        if (pos > 0) video.currentTime = pos;
        video.play().catch(() => showStatus('請點擊播放按鈕'));
        video.removeEventListener('loadedmetadata', onMeta);
      }, { once: true });
    }
  }

  function showControls() {
    if (isLocked) return;
    controls.classList.remove('hide');
    clearTimeout(controlsTimer);
    controlsTimer = setTimeout(() => {
      if (!video.paused) controls.classList.add('hide');
    }, 3500);
  }

  function toggleControls() {
    if (isLocked) return;
    if (controls.classList.contains('hide')) {
      showControls();
    } else {
      controls.classList.add('hide');
    }
  }

  document.getElementById('btnYouTube').addEventListener('click', () => {
    window.open('https://www.youtube.com', '_blank');
  });

  document.getElementById('btnPlayUrl').addEventListener('click', () => {
    const url = document.getElementById('urlInput').value.trim();
    if (!url) {
      alert('請輸入影片網址');
      return;
    }
    if (!url.startsWith('http')) {
      alert('網址需以 http 或 https 開頭');
      return;
    }
    playUrl(url);
  });

  document.getElementById('urlInput').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') document.getElementById('btnPlayUrl').click();
  });

  document.getElementById('btnBack').addEventListener('click', () => {
    savePosition();
    if (hls) { hls.destroy(); hls = null; }
    video.pause();
    video.removeAttribute('src');
    video.load();
    playerScreen.classList.add('hidden');
    homeScreen.classList.remove('hidden');
    currentUrl = '';
  });

  btnPlayPause.addEventListener('click', () => {
    if (video.paused) {
      video.play();
      btnPlayPause.textContent = '⏸';
    } else {
      video.pause();
      btnPlayPause.textContent = '▶';
      savePosition();
    }
    showControls();
  });

  document.getElementById('btnRewind').addEventListener('click', () => {
    video.currentTime = Math.max(0, video.currentTime - 10);
    showControls();
  });

  document.getElementById('btnForward').addEventListener('click', () => {
    video.currentTime = Math.min(video.duration || 0, video.currentTime + 10);
    showControls();
  });

  btnSpeed.addEventListener('click', () => {
    const speeds = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0];
    let idx = speeds.indexOf(video.playbackRate);
    idx = (idx + 1) % speeds.length;
    video.playbackRate = speeds[idx];
    btnSpeed.textContent = speeds[idx] + 'x';
    showControls();
  });

  btnLock.addEventListener('click', () => {
    isLocked = !isLocked;
    btnLock.textContent = isLocked ? '解鎖' : '鎖定';
    playerScreen.classList.toggle('locked', isLocked);
    if (!isLocked) showControls();
  });

  document.getElementById('btnFullscreen').addEventListener('click', () => {
    const el = document.querySelector('.player-wrapper');
    if (!document.fullscreenElement) {
      (el.requestFullscreen || el.webkitRequestFullscreen || el.msRequestFullscreen)?.call(el);
    } else {
      (document.exitFullscreen || document.webkitExitFullscreen)?.call(document);
    }
  });

  document.getElementById('btnQuality').addEventListener('click', () => {
    showStatus('網頁版由瀏覽器與 HLS.js 自動選擇畫質');
  });

  video.addEventListener('click', toggleControls);
  controls.addEventListener('click', (e) => {
    if (e.target === controls) toggleControls();
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
  video.addEventListener('pause', () => {
    btnPlayPause.textContent = '▶';
    savePosition();
  });
  video.addEventListener('ended', () => {
    btnPlayPause.textContent = '▶';
  });

  setInterval(savePosition, 8000);
  window.addEventListener('beforeunload', savePosition);
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) savePosition();
  });

})();
