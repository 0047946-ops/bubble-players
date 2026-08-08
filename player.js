/* StreamForge standalone player: network-aware playback, bounded recovery, state persistence, and mobile gestures. */
(() => {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const video = $("video"), surface = $("playerSurface"), sourceInput = $("source");
  const hlsSupported = window.Hls && Hls.isSupported();
  let hls = null, dash = null, sourceUrl = localStorage.getItem("sf-url") || sourceInput.value;
  let retryCount = 0, retryTimer = null, lastRecovery = 0, shouldResume = false;
  let gestureStart = null, lastTap = 0, hintTimer = null;
  const stateKey = "streamforge-standalone";

  const saved = JSON.parse(localStorage.getItem(stateKey) || "{}");
  if (saved.speed) $("speed").value = saved.speed;
  if (saved.url) sourceInput.value = saved.url;

  function format(seconds) {
    if (!Number.isFinite(seconds)) return "00:00";
    seconds = Math.max(0, Math.floor(seconds));
    return `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
  }
  function setStatus(text) { $("status").textContent = text; }
  function hint(text) {
    const el = $("gestureHint"); el.textContent = text; el.classList.add("visible");
    clearTimeout(hintTimer); hintTimer = setTimeout(() => el.classList.remove("visible"), 700);
  }
  function networkInfo() {
    const c = navigator.connection || navigator.mozConnection || navigator.webkitConnection || {};
    return { online: navigator.onLine, downlink: Number(c.downlink || 0), type: c.type || c.effectiveType || "未知連線", effective: c.effectiveType || "未知" };
  }
  function updateNetwork() {
    const n = networkInfo();
    $("online").textContent = n.online ? "● ONLINE" : "● OFFLINE";
    $("online").className = n.online ? "online" : "offline";
    $("speedReadout").textContent = n.downlink ? `${n.downlink.toFixed(1)} Mbps` : (n.online ? "已連線" : "斷線");
    $("networkType").textContent = `${n.type} · ${n.effective}`;
    [...$("signalBars").children].forEach((bar, i) => bar.classList.toggle("on", n.online && (n.downlink === 0 || n.downlink > [0, 1, 3, 8, 15][i])));
    return n;
  }
  function modeOf(url) { return /\.m3u8($|\?)/i.test(url) ? "HLS" : /\.mpd($|\?)/i.test(url) ? "DASH" : "NATIVE"; }
  function cleanup() { if (hls) { hls.destroy(); hls = null; } if (dash) { dash.reset(); dash = null; } if (retryTimer) clearTimeout(retryTimer); }
  function saveState() {
    localStorage.setItem(stateKey, JSON.stringify({ url: sourceUrl, position: video.currentTime || 0, speed: video.playbackRate || 1 }));
    localStorage.setItem("sf-url", sourceUrl);
  }
  function resumePosition(url) { return url === saved.url || url === sourceUrl ? Number(saved.position || 0) : 0; }
  function capHlsIfRisky(ahead) {
    if (!hls || !hls.levels.length) return;
    const n = networkInfo(), risky = ahead < 3 || video.readyState < 3;
    if (risky) { const cap = n.downlink && n.downlink < 3 ? 0 : Math.max(0, hls.levels.findIndex(x => x.bitrate <= 1800000)); hls.autoLevelCapping = cap; $("quality").textContent = "風險限制畫質"; }
    else { hls.autoLevelCapping = -1; $("quality").textContent = "自動畫質"; }
  }
  function recover(reason) {
    if (!sourceUrl || retryCount >= 4 || Date.now() - lastRecovery < 1800) return;
    lastRecovery = Date.now(); retryCount += 1; shouldResume = true;
    setStatus(`${reason} · 第 ${retryCount}/4 次恢復`);
    retryTimer = setTimeout(() => loadSource(sourceUrl, video.currentTime, true), Math.min(1000 * retryCount, 4000));
  }
  function loadSource(url, position = 0, autoplay = true) {
    if (!url) return;
    cleanup(); sourceUrl = url.trim(); sourceInput.value = sourceUrl; localStorage.setItem("sf-url", sourceUrl);
    $("mode").textContent = modeOf(sourceUrl); setStatus("Source loading · 正在準備來源…");
    const start = () => { const resume = position || resumePosition(sourceUrl); if (resume > 0 && Number.isFinite(resume)) video.currentTime = resume; if (autoplay) video.play().catch(() => setStatus("Source ready · 請按播放")); };
    if (/\.m3u8($|\?)/i.test(sourceUrl) && hlsSupported) {
      hls = new Hls({ enableWorker: true, lowLatencyMode: false, backBufferLength: 30, maxBufferLength: 24, maxMaxBufferLength: 45, capLevelToPlayerSize: true });
      hls.loadSource(sourceUrl); hls.attachMedia(video); hls.on(Hls.Events.MANIFEST_PARSED, start);
      hls.on(Hls.Events.ERROR, (_event, data) => { if (data.fatal) recover("HLS 串流錯誤"); }); return;
    }
    if (/\.mpd($|\?)/i.test(sourceUrl) && window.dashjs) {
      dash = dashjs.MediaPlayer().create(); dash.initialize(video, sourceUrl, autoplay); dash.on(dashjs.MediaPlayer.events.STREAM_INITIALIZED, start); dash.on(dashjs.MediaPlayer.events.ERROR, () => recover("DASH 串流錯誤")); return;
    }
    video.src = sourceUrl; video.load(); video.addEventListener("loadedmetadata", start, { once: true });
  }
  function seekBy(seconds) { if (!Number.isFinite(video.duration)) return; video.currentTime = Math.max(0, Math.min(video.duration, video.currentTime + seconds)); }
  function seekTo(position) { if (Number.isFinite(video.duration)) video.currentTime = Math.max(0, Math.min(video.duration, position)); }

  $("load").onclick = () => { retryCount = 0; saved.position = 0; loadSource(sourceInput.value, 0, true); };
  $("play").onclick = () => { if (video.paused) video.play().catch(() => setStatus("請檢查影片來源或 CORS")); else video.pause(); };
  $("back10").onclick = () => { seekBy(-10); hint("−10 秒"); };
  $("forward10").onclick = () => { seekBy(10); hint("+10 秒"); };
  $("speed").onchange = (e) => { video.playbackRate = Number(e.target.value); saveState(); };
  $("fullscreen").onclick = () => (surface.requestFullscreen || surface.webkitRequestFullscreen)?.call(surface);
  $("progress").oninput = (e) => seekTo(Number(e.target.value));

  surface.addEventListener("pointerdown", (e) => { if (e.pointerType === "mouse" && e.button !== 0) return; gestureStart = { x: e.clientX, y: e.clientY, at: Date.now() }; surface.setPointerCapture?.(e.pointerId); });
  surface.addEventListener("pointerup", (e) => {
    if (!gestureStart || !video.duration) return;
    const start = gestureStart; gestureStart = null; const dx = e.clientX - start.x, dy = e.clientY - start.y;
    if (Math.abs(dx) > 28 && Math.abs(dx) > Math.abs(dy) * 1.25) { const target = video.currentTime + (dx / Math.max(1, surface.clientWidth)) * video.duration; seekTo(target); hint(`滑動調整 · ${format(video.currentTime)}`); return; }
    if (Math.abs(dx) < 18 && Math.abs(dy) < 18 && Date.now() - start.at < 420) { const now = Date.now(); if (now - lastTap < 300) { const right = e.clientX > surface.getBoundingClientRect().left + surface.clientWidth / 2; seekBy(right ? 10 : -10); hint(right ? "+10 秒" : "−10 秒"); lastTap = 0; } else lastTap = now; }
  });
  surface.addEventListener("pointercancel", () => { gestureStart = null; });
  surface.addEventListener("contextmenu", (e) => e.preventDefault());

  video.addEventListener("play", () => { $("play").textContent = "Ⅱ"; setStatus("Source ready · 已啟用網路感知緩衝"); });
  video.addEventListener("pause", () => { $("play").textContent = "▶"; saveState(); });
  video.addEventListener("loadedmetadata", () => { $("progress").max = video.duration || 0; });
  video.addEventListener("timeupdate", () => {
    const ahead = video.buffered.length ? video.buffered.end(video.buffered.length - 1) - video.currentTime : 0;
    $("progress").value = video.currentTime || 0; $("time").textContent = `${format(video.currentTime)} / ${format(video.duration)}`; $("buffer").textContent = `${Math.max(0, ahead).toFixed(1)}s buffer`;
    $("bufferBar").style.width = video.duration ? `${Math.min(100, ((video.currentTime + ahead) / video.duration) * 100)}%` : "0%"; capHlsIfRisky(ahead); saveState();
  });
  video.addEventListener("progress", () => { const ahead = video.buffered.length ? video.buffered.end(video.buffered.length - 1) - video.currentTime : 0; capHlsIfRisky(ahead); });
  video.addEventListener("waiting", () => { setStatus("Buffer watch · 偵測到卡頓風險，正在降低壓力"); capHlsIfRisky(0); });
  video.addEventListener("error", () => recover("媒體播放錯誤"));
  video.addEventListener("ended", saveState);
  window.addEventListener("offline", () => { shouldResume = !video.paused; setStatus("Connection lost · 已保留播放位置"); updateNetwork(); });
  window.addEventListener("online", () => { updateNetwork(); if (shouldResume && sourceUrl) { setStatus("Connection recovered · Holding position"); retryCount = 0; loadSource(sourceUrl, video.currentTime, true); } });
  (navigator.connection || navigator.mozConnection || navigator.webkitConnection)?.addEventListener?.("change", updateNetwork);

  updateNetwork(); video.playbackRate = Number($("speed").value); loadSource(sourceUrl, Number(saved.position || 0), false);
})();
