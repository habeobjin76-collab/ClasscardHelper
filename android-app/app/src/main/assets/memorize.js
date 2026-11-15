(() => {
  const previous = window.__chMemorize;
  if (previous && previous.running) {
    const status = document.getElementById("__ch_memorize_status_text");
    if (status) status.textContent = "이미 암기 중입니다.";
    return "already-running";
  }
  const controller = { running: true, count: 0, stop: null };
  window.__chMemorize = controller;
  const oldPanel = document.getElementById("__ch_memorize_status_panel");
  if (oldPanel) oldPanel.remove();
  const panel = document.createElement("div");
  panel.id = "__ch_memorize_status_panel";
  panel.setAttribute("role", "status");
  panel.setAttribute("aria-live", "polite");
  panel.style.cssText = "position:fixed;z-index:2147483647;left:12px;top:12px;max-width:calc(100vw - 24px);display:flex;align-items:center;gap:10px;padding:10px 12px;background:#18243a;color:#fff;border-radius:12px;box-shadow:0 3px 14px #0008;font:14px sans-serif;";
  const label = document.createElement("span");
  label.id = "__ch_memorize_status_text";
  label.textContent = "암기 준비 중";
  panel.appendChild(label);
  const stopButton = document.createElement("button");
  stopButton.type = "button";
  stopButton.textContent = "정지";
  stopButton.setAttribute("aria-label", "암기 자동화 정지");
  stopButton.style.cssText = "border:0;border-radius:8px;padding:7px 10px;background:#dc2626;color:#fff;font-weight:700;";
  panel.appendChild(stopButton);
  (document.body || document.documentElement).appendChild(panel);
  const update = (message) => { label.textContent = message; };
  const finish = (message) => {
    controller.running = false;
    update(message + " · " + controller.count + "장 처리");
    stopButton.disabled = true;
    stopButton.style.opacity = "0.65";
  };
  controller.stop = () => {
    if (!controller.running) return;
    controller.running = false;
    update("중지 요청됨 · " + controller.count + "장 처리");
  };
  stopButton.addEventListener("click", controller.stop);
  const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  const waitFor = async (find, timeout = 2000) => {
    const started = Date.now();
    while (controller.running && Date.now() - started < timeout) {
      const result = find();
      if (result) return result;
      await sleep(50);
    }
    return null;
  };
  const run = async () => {
    try {
      if (!document.querySelector(".CardItem.showing.current")) {
        finish("암기 카드 화면에서 시작해 주세요");
        return;
      }
      while (controller.running) {
        const card = document.querySelector(".CardItem.showing.current");
        if (!card) { finish("카드가 더 이상 없습니다"); return; }
        const cover = card.querySelector(".card-cover");
        if (cover) {
          cover.click();
          const knownAfterFlip = await waitFor(() => card.querySelector(".btn_kown_o"));
          if (!controller.running) break;
          if (!knownAfterFlip) { finish("카드를 넘기는 중 버튼을 찾지 못했습니다"); return; }
        }
        const known = card.querySelector(".btn_kown_o");
        if (!known) { finish("알고 있어요 버튼을 찾지 못했습니다"); return; }
        known.click();
        controller.count += 1;
        update("암기 중 · " + controller.count + "장 처리 · 정지 가능");
        const next = await waitFor(() => document.querySelector(".btnNextCard2"));
        if (!controller.running) break;
        if (!next) { finish("다음 카드 버튼을 찾지 못했습니다"); return; }
        next.click();
        const changed = await waitFor(() => {
          const visible = document.querySelector(".CardItem.showing.current");
          return visible && visible !== card ? visible : null;
        });
        if (!controller.running) break;
        if (!changed) { finish("다음 카드를 기다리다 멈췄습니다"); return; }
      }
      finish("중지됨");
    } catch (error) {
      finish("오류: " + (error && error.message ? error.message : "실행 실패"));
    }
  };
  run();
  return "started";
})();
