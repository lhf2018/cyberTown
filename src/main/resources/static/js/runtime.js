(() => {
  const CLIENT_KEY = localStorage.getItem("cybertown.clientKey") || (() => {
    const k = "ct-" + Math.random().toString(36).slice(2, 10);
    localStorage.setItem("cybertown.clientKey", k);
    return k;
  })();

  const connectionStatus = document.getElementById("connectionStatus");
  const opStatus = document.getElementById("opStatus");
  const npcGrid = document.getElementById("npcGrid");
  const npcCountText = document.getElementById("npcCountText");
  const mTotal = document.getElementById("mTotal");
  const mHungry = document.getElementById("mHungry");
  const mTired = document.getElementById("mTired");
  const mHappy = document.getElementById("mHappy");
  const mUpdated = document.getElementById("mUpdated");
  const worldBroadcast = document.getElementById("worldBroadcast");
  const worldBroadcastTime = document.getElementById("worldBroadcastTime");
  const newsList = document.getElementById("newsList");
  const newsBrief = document.getElementById("newsBrief");
  const newsUpdatedAt = document.getElementById("newsUpdatedAt");
  const godNpcSelect = document.getElementById("godNpcSelect");
  const godInstruction = document.getElementById("godInstruction");
  const godStatus = document.getElementById("godStatus");
  const godNpcReply = document.getElementById("godNpcReply");
  const thoughtLayer = document.getElementById("thoughtLayer");
  const talkModalMask = document.getElementById("talkModalMask");
  const talkModalTitle = document.getElementById("talkModalTitle");
  const chatList = document.getElementById("chatList");
  const talkMessageInput = document.getElementById("talkMessageInput");
  const talkModalStatus = document.getElementById("talkModalStatus");
  const profileModalMask = document.getElementById("profileModalMask");
  const profileModalTitle = document.getElementById("profileModalTitle");
  const profileModalSubtitle = document.getElementById("profileModalSubtitle");
  const profileModalContent = document.getElementById("profileModalContent");
  const eventTimeline = document.getElementById("eventTimeline");
  const townMap = document.getElementById("townMap");
  const mapFilterLabel = document.getElementById("mapFilterLabel");
  const modeBadge = document.getElementById("modeBadge");
  const mainTabs = document.getElementById("mainTabs");
  const showRelationsChk = document.getElementById("showRelationsChk");
  const relCountText = document.getElementById("relCountText");
  const relList = document.getElementById("relList");
  const questTitle = document.getElementById("questTitle");
  const questDesc = document.getElementById("questDesc");
  const questProgressFill = document.getElementById("questProgressFill");
  const questProgressText = document.getElementById("questProgressText");
  const questStatus = document.getElementById("questStatus");
  const questTip = document.getElementById("questTip");
  const replaySlider = document.getElementById("replaySlider");
  const replayCard = document.getElementById("replayCard");
  const replayRail = document.getElementById("replayRail");
  const replayPosLabel = document.getElementById("replayPosLabel");
  const aiMetricsUpdated = document.getElementById("aiMetricsUpdated");
  const aiTotal = document.getElementById("aiTotal");
  const aiFailRate = document.getElementById("aiFailRate");
  const aiAvg = document.getElementById("aiAvg");
  const aiRecent = document.getElementById("aiRecent");
  const worldTagPill = document.getElementById("worldTagPill");
  const worldModList = document.getElementById("worldModList");
  const decisionSummary = document.getElementById("decisionSummary");
  const decisionTable = document.getElementById("decisionTable");
  const saveNameInput = document.getElementById("saveNameInput");
  const saveSlots = document.getElementById("saveSlots");
  const dailyPoster = document.getElementById("dailyPoster");
  const soundToggleBtn = document.getElementById("soundToggleBtn");

  let source = null;
  let talkStreamSource = null;
  let currentNpcMap = new Map();
  let selectedTalkNpcId = null;
  let currentMode = "operator";
  let locationCache = [];
  let selectedLocation = null;
  let activeTab = "overview";
  let relationshipGraph = { nodes: [], edges: [] };
  let replayEvents = [];
  let replayIndex = 0;
  let dailyReportCache = null;
  let lastEventFingerprint = "";
  let soundEnabled = localStorage.getItem("cybertown.sound") !== "off";
  let audioCtx = null;

  const MAP_LAYOUT = {
    "警察总局": { x: 18, y: 18, type: "治安" },
    "赛博诊所": { x: 38, y: 16, type: "医疗" },
    "科技公司": { x: 62, y: 18, type: "工作" },
    "全息商场": { x: 84, y: 22, type: "消费" },
    "霓虹街道": { x: 50, y: 48, type: "主干道" },
    "仿生餐厅": { x: 22, y: 48, type: "餐饮" },
    "中央公园": { x: 78, y: 48, type: "休闲" },
    "地下黑市": { x: 18, y: 78, type: "灰市" },
    "霓虹酒吧": { x: 50, y: 82, type: "社交" },
    "公寓住宅": { x: 82, y: 78, type: "居住" },
    "网络空间": { x: 50, y: 28, type: "虚拟" }
  };

  const MAP_ROADS = [
    ["警察总局", "霓虹街道"],
    ["赛博诊所", "霓虹街道"],
    ["科技公司", "霓虹街道"],
    ["全息商场", "霓虹街道"],
    ["仿生餐厅", "霓虹街道"],
    ["中央公园", "霓虹街道"],
    ["地下黑市", "霓虹街道"],
    ["霓虹酒吧", "霓虹街道"],
    ["公寓住宅", "霓虹街道"]
  ];

  function headers(json = false) {
    const h = { "X-Client-Key": CLIENT_KEY };
    if (json) h["Content-Type"] = "application/json";
    return h;
  }

  function escapeHtml(text) {
    return String(text ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;");
  }

  function isToxicDecisionReason(reason) {
    if (!reason) return false;
    const r = String(reason).toLowerCase();
    return r.includes("expected begin_object")
      || r.includes("illegalstateexception")
      || r.includes("ai失败:")
      || r.includes("exception:")
      || r.includes(" at line ")
      || r.includes("path $");
  }

  function sanitizeDecisionReason(reason) {
    if (!reason || !String(reason).trim()) return "综合考虑NPC状态和环境";
    if (isToxicDecisionReason(reason)) return "规则引擎决策（AI暂不可用）";
    const trimmed = String(reason).trim();
    return trimmed.length > 180 ? trimmed.substring(0, 179) + "…" : trimmed;
  }

  function setOpStatus(text, isError = false) {
    opStatus.style.color = isError ? "#f87171" : "#93a2c5";
    opStatus.textContent = text;
  }

  function clamp(v) {
    return Math.max(0, Math.min(100, Number(v) || 0));
  }

  function initAudio() {
    if (!audioCtx) {
      try {
        audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      } catch (_) {}
    }
  }

  function playSoftBeep() {
    if (!soundEnabled) return;
    initAudio();
    if (!audioCtx) return;
    try {
      if (audioCtx.state === "suspended") audioCtx.resume();
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();
      osc.type = "sine";
      osc.frequency.value = 520;
      gain.gain.value = 0.04;
      osc.connect(gain);
      gain.connect(audioCtx.destination);
      osc.start();
      gain.gain.exponentialRampToValueAtTime(0.0001, audioCtx.currentTime + 0.12);
      osc.stop(audioCtx.currentTime + 0.13);
    } catch (_) {}
  }

  function updateSoundBtn() {
    soundToggleBtn.textContent = soundEnabled ? "声音：开" : "声音：关";
  }

  async function postJson(url, body) {
    const resp = await fetch(url, {
      method: "POST",
      headers: headers(body !== undefined),
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    if (!resp.ok) {
      let msg = `请求失败: ${url}`;
      try {
        const err = await resp.json();
        if (err.message) msg = err.message;
      } catch (_) {}
      throw new Error(msg);
    }
    const ct = resp.headers.get("content-type") || "";
    if (ct.includes("application/json")) return resp.json();
    return resp.text();
  }

  async function deleteJson(url) {
    const resp = await fetch(url, { method: "DELETE", headers: headers() });
    if (!resp.ok) {
      let msg = `删除失败: ${url}`;
      try {
        const err = await resp.json();
        if (err.message) msg = err.message;
      } catch (_) {}
      throw new Error(msg);
    }
    const ct = resp.headers.get("content-type") || "";
    if (ct.includes("application/json")) return resp.json();
    return resp.text();
  }

  function isSpectator() {
    return currentMode === "spectator";
  }

  function applyModeUi(status) {
    if (!status) return;
    currentMode = status.mode || "operator";
    const talk = status.talkRemaining ?? "-";
    const god = status.godRemaining ?? "-";
    modeBadge.textContent = currentMode === "spectator"
      ? `旁观中 · 对话剩余 ${talk} · 上帝剩余 ${god}`
      : `运营中 · 对话剩余 ${talk} · 上帝剩余 ${god}`;
    document.querySelectorAll(".operator-only").forEach((el) => {
      el.classList.toggle("hidden", isSpectator());
    });
    document.getElementById("toggleModeBtn").textContent =
      currentMode === "spectator" ? "切换为运营模式" : "切换为旁观模式";
  }

  async function refreshMode() {
    try {
      const resp = await fetch("/api/town/mode", { headers: headers() });
      if (!resp.ok) return;
      applyModeUi(await resp.json());
    } catch (_) {}
  }

  async function toggleMode() {
    const next = currentMode === "spectator" ? "operator" : "spectator";
    try {
      const data = await postJson("/api/town/mode", { mode: next });
      applyModeUi(data);
      renderNPCList([...currentNpcMap.values()]);
    } catch (e) {
      setOpStatus(e.message, true);
    }
  }

  function getMoodText(happiness) {
    if (happiness > 80) return "高兴";
    if (happiness > 60) return "还行";
    if (happiness > 40) return "一般";
    if (happiness > 20) return "沮丧";
    return "痛苦";
  }

  function getStatusSummary(stats) {
    const tags = [];
    if (stats.hunger > 70) tags.push("饥饿");
    if (stats.energy < 30) tags.push("疲劳");
    if (stats.happiness < 30) tags.push("低落");
    if (stats.socialNeed > 70) tags.push("需社交");
    return tags.length ? tags.join("、") : "正常";
  }

  function getNpcAvatar(occupation) {
    const occ = occupation || "";
    if (occ.includes("程序员") || occ.includes("黑客")) return "🧑‍💻";
    if (occ.includes("警察") || occ.includes("保安")) return "👮";
    if (occ.includes("医生")) return "🧑‍⚕️";
    if (occ.includes("设计")) return "🎨";
    if (occ.includes("司机")) return "🚕";
    if (occ.includes("舞者")) return "💃";
    if (occ.includes("酒吧")) return "🍸";
    return "🤖";
  }

  function relEdgeColor(type) {
    const t = String(type || "").toUpperCase();
    if (t === "FRIEND" || t === "LOVER") return "#35e0d0";
    if (t === "RIVAL") return "#ff6b7a";
    return "#6b849f";
  }

  function questStatusLabel(status) {
    const s = String(status || "").toUpperCase();
    if (s === "DONE") return "已完成";
    if (s === "FAILED") return "失败";
    if (s === "ACTIVE") return "进行中";
    return status || "-";
  }

  function renderNPCList(list) {
    const npcs = Array.isArray(list) ? list : [];
    const filtered = selectedLocation
      ? npcs.filter((n) => (n.location || n.currentLocation) === selectedLocation)
      : npcs;
    npcCountText.textContent = selectedLocation
      ? `${selectedLocation} · ${filtered.length} 人（全镇 ${npcs.length}）`
      : `共 ${npcs.length} 人`;
    npcGrid.innerHTML = filtered.map((n) => {
      const energy = Number(n.energy ?? n.stats?.energy ?? 0);
      const hunger = Number(n.hunger ?? n.stats?.hunger ?? 0);
      const happiness = Number(n.happiness ?? n.stats?.happiness ?? 0);
      const socialNeed = Number(n.socialNeed ?? n.stats?.socialNeed ?? 0);
      const mood = n.mood ?? getMoodText(happiness);
      const statusSummary = n.statusSummary ?? getStatusSummary({ energy, hunger, happiness, socialNeed });
      const goal = n.currentGoal ?? n.goal ?? "-";
      const skillLevel = Number(n.skillLevel ?? n.stats?.skillLevel ?? 0);
      const knowledgeLevel = Number(n.knowledgeLevel ?? n.stats?.knowledgeLevel ?? 0);
      const reputation = Number(n.reputation ?? n.stats?.reputation ?? 0);
      const health = Number(n.health ?? n.stats?.health ?? 0);
      const technicalKnowledge = Number(n.technicalKnowledge ?? n.stats?.technicalKnowledge ?? 0);
      const businessKnowledge = Number(n.businessKnowledge ?? n.stats?.businessKnowledge ?? 0);
      const socialKnowledge = Number(n.socialKnowledge ?? n.stats?.socialKnowledge ?? 0);
      const practicalSkill = Number(n.practicalSkill ?? n.stats?.practicalSkill ?? 0);
      const creativeSkill = Number(n.creativeSkill ?? n.stats?.creativeSkill ?? 0);
      const employer = n.employer || "自由职业";
      const avatar = getNpcAvatar(n.occupation || "");
      const reason = sanitizeDecisionReason(n.lastDecisionReason || "");
      const decision = n.lastDecision || n.action || n.currentAction || "-";
      const safeId = escapeHtml(n.id);
      return `
        <div class="card">
          <div class="head">
            <div class="avatar">${avatar}</div>
            <div>
              <div class="name">${escapeHtml(n.name || "未知")} · ${escapeHtml(n.occupation || "-")}</div>
              <div class="sub">就职于：${escapeHtml(employer)}</div>
            </div>
          </div>
          <div class="line">位置：${escapeHtml(n.location || n.currentLocation || "-")}</div>
          <div class="line">动作：${escapeHtml(decision)}</div>
          <div class="line">目标：${escapeHtml(goal)}</div>
          <div class="line">状态：${escapeHtml(statusSummary)}</div>
          <div class="line">心情：${escapeHtml(mood)}</div>
          ${reason ? `<div class="reason" onclick="this.classList.toggle('open')" title="点击展开">决策理由：${escapeHtml(reason)}</div>` : ""}
          <div class="line">总技能：${skillLevel} | 总知识：${knowledgeLevel} | 声望：${reputation} | 健康：${health}</div>
          <div class="skills">
            <span class="chip">技术知识 ${technicalKnowledge}</span>
            <span class="chip">商业知识 ${businessKnowledge}</span>
            <span class="chip">人际知识 ${socialKnowledge}</span>
            <span class="chip">实操技能 ${practicalSkill}</span>
            <span class="chip">创造技能 ${creativeSkill}</span>
          </div>
          <div class="row">
            <button class="link-btn" onclick="openProfileModal('${safeId}', 'assets')">资产详情</button>
            <button class="link-btn" onclick="openProfileModal('${safeId}', 'education')">学历详情</button>
            <button class="link-btn" onclick="openProfileModal('${safeId}', 'compensation')">工资详情</button>
            <button class="link-btn" onclick="openRelationships('${safeId}')">人际关系</button>
          </div>
          <div class="line">能量 ${energy}</div><div class="bar"><span style="width:${clamp(energy)}%"></span></div>
          <div class="line">饥饿 ${hunger}</div><div class="bar"><span style="width:${clamp(hunger)}%"></span></div>
          <div class="line">快乐 ${happiness}</div><div class="bar"><span style="width:${clamp(happiness)}%"></span></div>
          <div class="line">社交需求 ${socialNeed}</div><div class="bar"><span style="width:${clamp(socialNeed)}%"></span></div>
          <button class="tiny-btn operator-only ${isSpectator() ? "hidden" : ""}" onclick="openTalkModal('${safeId}')">打开对话</button>
        </div>`;
    }).join("");
    currentNpcMap = new Map(npcs.map((n) => [n.id, n]));
    syncGodNpcOptions(npcs);
    renderMap(npcs);
  }

  function buildMapPoints(npcs) {
    const byLoc = {};
    (npcs || []).forEach((n) => {
      const name = n.location || n.currentLocation || "未知";
      if (!byLoc[name]) byLoc[name] = [];
      byLoc[name].push(n);
    });
    const names = new Set([
      ...Object.keys(MAP_LAYOUT),
      ...locationCache.map((l) => l.name),
      ...Object.keys(byLoc)
    ]);
    const points = {};
    [...names].forEach((name, idx) => {
      if (MAP_LAYOUT[name]) {
        points[name] = MAP_LAYOUT[name];
      } else {
        const fromApi = locationCache.find((l) => l.name === name);
        points[name] = {
          x: fromApi ? Number(fromApi.mapX) || (20 + (idx % 4) * 20) : (20 + (idx % 4) * 20),
          y: fromApi ? Number(fromApi.mapY) || (20 + Math.floor(idx / 4) * 25) : (20 + Math.floor(idx / 4) * 25),
          type: fromApi?.type || "其他"
        };
      }
    });
    return { points, byLoc };
  }

  function buildRelationshipLines(points) {
    if (!showRelationsChk?.checked || !relationshipGraph.edges?.length) return "";
    const locByNpcId = {};
    (relationshipGraph.nodes || []).forEach((n) => {
      if (n.id && n.location) locByNpcId[n.id] = n.location;
    });
    currentNpcMap.forEach((n, id) => {
      locByNpcId[id] = n.location || n.currentLocation || locByNpcId[id];
    });
    const drawn = new Set();
    return relationshipGraph.edges.map((edge) => {
      const locA = locByNpcId[edge.source];
      const locB = locByNpcId[edge.target];
      if (!locA || !locB || !points[locA] || !points[locB]) return "";
      const key = [locA, locB].sort().join("|") + "|" + (edge.type || "");
      if (drawn.has(key)) return "";
      drawn.add(key);
      const p1 = points[locA];
      const p2 = points[locB];
      const affinity = Math.abs(Number(edge.affinity) || 0);
      const opacity = Math.max(0.15, Math.min(1, affinity / 100));
      const color = relEdgeColor(edge.type);
      return `<line x1="${p1.x}%" y1="${p1.y}%" x2="${p2.x}%" y2="${p2.y}%"
        stroke="${color}" stroke-opacity="${opacity.toFixed(2)}" stroke-width="2.5"/>`;
    }).join("");
  }

  function renderMap(npcs) {
    if (!townMap) return;
    const { points, byLoc } = buildMapPoints(npcs);
    const roadsSvg = MAP_ROADS.map(([a, b]) => {
      if (!points[a] || !points[b]) return "";
      return `<line x1="${points[a].x}%" y1="${points[a].y}%" x2="${points[b].x}%" y2="${points[b].y}%"
        stroke="rgba(90,140,200,0.28)" stroke-width="2" stroke-dasharray="6 8"/>`;
    }).join("");
    const relSvg = buildRelationshipLines(points);
    const nodesHtml = Object.keys(points).map((name) => {
      const p = points[name];
      const crowd = byLoc[name] || [];
      const count = crowd.length;
      const active = selectedLocation === name ? "active" : "";
      const empty = count === 0 ? "empty" : "";
      const avatars = crowd.slice(0, 5).map((n) =>
        `<span class="map-avatar" title="${escapeHtml(n.name || "")}">${getNpcAvatar(n.occupation || "")}</span>`
      ).join("");
      const more = count > 5 ? `<span class="map-avatar">+${count - 5}</span>` : "";
      return `
        <div class="map-node ${active} ${empty}" data-name="${escapeHtml(name)}"
             style="left:${p.x}%; top:${p.y}%;">
          <div class="map-node-top">
            <div class="map-node-name">${escapeHtml(name)}</div>
            <div class="map-node-count ${count >= 2 ? "hot" : ""}">${count}</div>
          </div>
          <div class="map-node-type">${escapeHtml(p.type || "")}</div>
          ${count ? `<div class="map-node-people">${avatars}${more}</div>` : ""}
        </div>`;
    }).join("");
    townMap.innerHTML = `
      <svg class="map-roads" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
        ${roadsSvg}
        <circle cx="50" cy="48" r="1.8" fill="rgba(53,224,208,0.55)" />
      </svg>
      <svg class="rel-overlay map-roads" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
        ${relSvg}
      </svg>
      ${nodesHtml}`;
    townMap.querySelectorAll(".map-node").forEach((node) => {
      node.addEventListener("click", () => {
        const name = node.getAttribute("data-name");
        selectedLocation = selectedLocation === name ? null : name;
        mapFilterLabel.textContent = selectedLocation
          ? `筛选中：${selectedLocation}（再点取消）`
          : "点击区块筛选居民 · 再点取消";
        renderNPCList([...currentNpcMap.values()]);
      });
    });
  }

  function renderRelList() {
    const edges = relationshipGraph.edges || [];
    relCountText.textContent = edges.length ? `共 ${edges.length} 条关系` : "暂无关系";
    if (!edges.length) {
      relList.innerHTML = "<div class='line'>暂无已记录关系，等同地点社交发生后会出现。</div>";
      return;
    }
    relList.innerHTML = edges.map((e) => `
      <div class="rel-item">
        <div class="names">${escapeHtml(e.sourceName || "?")} ↔ ${escapeHtml(e.targetName || "?")}</div>
        <div class="line">${escapeHtml(e.type || "ACQUAINTANCE")} · 好感 ${Number(e.affinity) || 0}</div>
        ${e.note ? `<div class="line">${escapeHtml(e.note)}</div>` : ""}
      </div>`).join("");
  }

  async function refreshRelationshipGraph() {
    try {
      const resp = await fetch("/api/town/relationships/graph", { headers: headers() });
      if (!resp.ok) return;
      relationshipGraph = await resp.json();
      renderRelList();
      renderMap([...currentNpcMap.values()]);
    } catch (_) {}
  }

  function openTalkModal(id) {
    if (isSpectator()) {
      setOpStatus("旁观模式不可对话", true);
      return;
    }
    selectedTalkNpcId = id;
    const npc = currentNpcMap.get(id);
    talkModalTitle.textContent = `与 ${npc?.name || "NPC"} 对话`;
    renderChatFromMemory(npc?.dialogueMemory || "", npc?.name || "NPC");
    talkModalStatus.textContent = "";
    talkMessageInput.value = "";
    talkModalMask.style.display = "flex";
    talkMessageInput.focus();
  }

  function closeTalkModal() {
    if (talkStreamSource) {
      talkStreamSource.close();
      talkStreamSource = null;
    }
    talkModalMask.style.display = "none";
    selectedTalkNpcId = null;
  }

  function closeProfileModal() {
    profileModalMask.style.display = "none";
  }

  async function openRelationships(id) {
    const npc = currentNpcMap.get(id);
    profileModalTitle.textContent = `${npc?.name || "NPC"} · 人际关系`;
    profileModalSubtitle.textContent = `${npc?.occupation || "-"} @ ${npc?.employer || "自由职业"}`;
    profileModalContent.innerHTML = "<div class='line'>加载中...</div>";
    profileModalMask.style.display = "flex";
    try {
      const resp = await fetch(`/api/town/npc/${id}/relationships`, { headers: headers() });
      if (!resp.ok) throw new Error("读取关系失败");
      const data = await resp.json();
      const list = Array.isArray(data.relationships) ? data.relationships : [];
      if (!list.length) {
        profileModalContent.innerHTML = "<div class='line'>暂无已记录关系，等同地点社交发生后会出现。</div>";
        return;
      }
      profileModalContent.innerHTML = `<ul class="detail-list">${list.map((r) =>
        `<li><strong>${escapeHtml(r.otherName)}</strong>（${escapeHtml(r.otherOccupation || "-")}）
         · ${escapeHtml(r.type)} · 好感 ${r.affinity}
         <br><span class="line">${escapeHtml(r.note || "")}</span></li>`
      ).join("")}</ul>`;
    } catch (e) {
      profileModalContent.innerHTML = `<div class="line">${escapeHtml(e.message)}</div>`;
    }
  }

  async function openProfileModal(id, tab) {
    const npc = currentNpcMap.get(id);
    const tabTitle = tab === "assets" ? "资产详情" : (tab === "education" ? "学历详情" : "工资详情");
    profileModalTitle.textContent = `${npc?.name || "NPC"} · ${tabTitle}`;
    profileModalSubtitle.textContent = `${npc?.occupation || "-"} @ ${npc?.employer || "自由职业"}`;
    profileModalContent.innerHTML = "<div class='line'>加载中...</div>";
    profileModalMask.style.display = "flex";
    try {
      const resp = await fetch(`/api/town/npc/${id}/profile/extended`, { headers: headers() });
      if (!resp.ok) throw new Error("读取详情失败");
      const data = await resp.json();
      if (tab === "assets") {
        const a = data.assets || {};
        profileModalContent.innerHTML = `
          <ul class="detail-list">
            <li>现金：${Number(a.cash || 0).toFixed(2)} ${escapeHtml(a.currency || "元")}</li>
            <li>储蓄：${Number(a.savings || 0).toFixed(2)} ${escapeHtml(a.currency || "元")}</li>
            <li>负债：${Number(a.debt || 0).toFixed(2)} ${escapeHtml(a.currency || "元")}</li>
            <li>总资产：${Number(a.totalAssets || 0).toFixed(2)} ${escapeHtml(a.currency || "元")}</li>
            <li>净资产：${Number(a.netWorth || 0).toFixed(2)} ${escapeHtml(a.currency || "元")}</li>
            <li>流动性比例：${Number(a.liquidityRatio || 0).toFixed(2)}%</li>
            <li>负债资产比：${Number(a.debtToAssetRatio || 0).toFixed(2)}%</li>
            <li>财务风险等级：${escapeHtml(a.riskLevel || "未知")}</li>
          </ul>`;
      } else if (tab === "education") {
        const timeline = Array.isArray(data.educationTimeline) ? data.educationTimeline : [];
        const items = timeline.map((t) =>
          `<li>${escapeHtml(t.stage)} · ${escapeHtml(t.school || "-")}<br><span class="line">${escapeHtml(t.focus || "")}</span></li>`
        ).join("");
        profileModalContent.innerHTML = `
          <div class="line">当前学历：${escapeHtml(data.educationLevel || "-")}，从业时长：${data.workExperienceMonths || 0} 月</div>
          <ul class="detail-list">${items || "<li>暂无教育轨迹</li>"}</ul>`;
      } else {
        const c = data.compensation || {};
        const optionPart = c.hasStockOption
          ? `${Number(c.monthlyStockOption || 0).toFixed(2)} ${escapeHtml(c.currency || "元")}`
          : "无（当前岗位/学历/公司不提供）";
        profileModalContent.innerHTML = `
          <ul class="detail-list">
            <li>月薪（现金部分）：${Number(c.monthlyCash || 0).toFixed(2)} ${escapeHtml(c.currency || "元")}</li>
            <li>月度股票期权：${optionPart}</li>
            <li>月总收入：${Number(c.monthlyTotal || 0).toFixed(2)} ${escapeHtml(c.currency || "元")}（约 ${escapeHtml(c.monthlyDisplay || "-")}）</li>
            <li>年总收入：${Number(c.annualTotal || 0).toFixed(2)} ${escapeHtml(c.currency || "元")}（约 ${escapeHtml(c.annualDisplay || "-")}）</li>
            <li>收入等级：${escapeHtml(c.payLevel || "未知")}</li>
          </ul>`;
      }
    } catch (e) {
      profileModalContent.innerHTML = `<div class="line">${escapeHtml(e.message)}</div>`;
    }
  }

  function renderChatFromMemory(memory, npcName) {
    chatList.innerHTML = "";
    if (!memory || !String(memory).trim()) {
      appendMessageBubble("npc", `${npcName} 在等你开口。`, npcName);
      return;
    }
    const lines = String(memory).split("\n").filter(Boolean);
    for (const line of lines) {
      const parts = line.split(" | ");
      if (parts.length >= 2) {
        const userPart = parts[0].replace(/^玩家:\s*/, "");
        const npcPart = parts[1].replace(new RegExp(`^${npcName}:\\s*`), "");
        appendMessageBubble("me", userPart, "你");
        appendMessageBubble("npc", npcPart, npcName);
      } else {
        appendMessageBubble("npc", line, npcName);
      }
    }
    chatList.scrollTop = chatList.scrollHeight;
  }

  function appendMessageBubble(role, text, sender, typing = false) {
    const row = document.createElement("div");
    row.className = `msg-row ${role}`;
    const wrap = document.createElement("div");
    const meta = document.createElement("div");
    meta.className = "msg-meta";
    meta.textContent = sender || (role === "me" ? "你" : "NPC");
    const bubble = document.createElement("div");
    bubble.className = `msg-bubble ${typing ? "typing" : ""}`;
    bubble.textContent = text || "";
    wrap.appendChild(meta);
    wrap.appendChild(bubble);
    row.appendChild(wrap);
    chatList.appendChild(row);
    chatList.scrollTop = chatList.scrollHeight;
    return bubble;
  }

  function syncGodNpcOptions(npcs) {
    const currentValue = godNpcSelect.value;
    godNpcSelect.innerHTML = npcs.map((n) =>
      `<option value="${escapeHtml(n.id)}">${escapeHtml(n.name)} (${escapeHtml(n.occupation || "-")})</option>`
    ).join("");
    if (currentValue) godNpcSelect.value = currentValue;
  }

  async function refreshSummary() {
    const [summaryResp, npcsResp] = await Promise.all([
      fetch("/api/town/stats/summary", { headers: headers() }),
      fetch("/api/town/npcs", { headers: headers() })
    ]);
    if (!summaryResp.ok || !npcsResp.ok) throw new Error("读取汇总信息失败");
    const summary = await summaryResp.json();
    const npcs = await npcsResp.json();
    if (!Array.isArray(npcs) || npcs.length === 0) {
      window.location.href = "/";
      return;
    }
    mTotal.textContent = summary.total ?? npcs.length ?? "-";
    mHungry.textContent = summary.hungryCount ?? "-";
    mTired.textContent = summary.tiredCount ?? "-";
    mHappy.textContent = summary.happyCount ?? "-";
    mUpdated.textContent = new Date().toLocaleTimeString();
    renderNPCList(npcs);
  }

  function applyRealtimePayload(data) {
    if (!data || !Array.isArray(data.npcs)) return;
    renderNPCList(data.npcs);
    mTotal.textContent = data.count ?? data.npcs.length;
    mUpdated.textContent = new Date().toLocaleTimeString();
  }

  function connectSSE() {
    if (source) source.close();
    source = new EventSource("/api/town/npcs/status/stream");
    connectionStatus.textContent = "SSE 连接中...";
    source.addEventListener("initial", (ev) => {
      applyRealtimePayload(JSON.parse(ev.data));
      connectionStatus.textContent = "SSE 已连接";
    });
    source.addEventListener("update", (ev) => {
      const data = JSON.parse(ev.data);
      if (data.type === "HEARTBEAT") return;
      applyRealtimePayload(data);
      connectionStatus.textContent = "实时更新中";
      refreshEvents();
      refreshLocations();
      refreshQuest();
    });
    source.addEventListener("connected", () => {
      connectionStatus.textContent = "SSE 已连接";
    });
    source.onerror = () => {
      connectionStatus.textContent = "SSE断开，使用轮询";
    };
  }

  async function refreshWorldBroadcast() {
    try {
      const resp = await fetch("/api/town/world/broadcast", { headers: headers() });
      if (!resp.ok) return;
      const data = await resp.json();
      worldBroadcast.textContent = data.message || "暂无新的世界广播";
      worldBroadcastTime.textContent = "更新时间: " + new Date(data.timestamp || Date.now()).toLocaleTimeString()
        + (data.source ? ` · ${data.source}` : "");
    } catch (_) {}
  }

  function eventFingerprint(events) {
    if (!events.length) return "";
    const first = events[0];
    const last = events[events.length - 1];
    return `${events.length}|${first.id || first.title}|${last.id || last.title}`;
  }

  async function refreshEvents() {
    try {
      const resp = await fetch("/api/town/events?limit=30", { headers: headers() });
      if (!resp.ok) return;
      const data = await resp.json();
      const events = Array.isArray(data.events) ? data.events : [];
      const fp = eventFingerprint(events);
      if (fp && fp !== lastEventFingerprint) {
        if (lastEventFingerprint) playSoftBeep();
        lastEventFingerprint = fp;
      }
      if (!events.length) {
        eventTimeline.innerHTML = "<div class='line'>暂无事件，等待心跳后出现社交/世界/人生动态。</div>";
        return;
      }
      eventTimeline.innerHTML = events.map((e) => `
        <div class="timeline-item">
          <span class="timeline-type type-${escapeHtml(e.type || "LIFE")}">${escapeHtml(e.type || "-")}</span>
          <strong>${escapeHtml(e.title || "")}</strong>
          <div class="line">${escapeHtml(e.detail || "")}</div>
          <div class="line">${e.createdAt ? new Date(e.createdAt).toLocaleString() : ""}</div>
        </div>`).join("");
    } catch (_) {}
  }

  async function refreshLocations() {
    try {
      const resp = await fetch("/api/town/locations", { headers: headers() });
      if (!resp.ok) return;
      const data = await resp.json();
      locationCache = Array.isArray(data.locations) ? data.locations : [];
      renderMap([...currentNpcMap.values()]);
    } catch (_) {}
  }

  async function refreshQuest() {
    try {
      const resp = await fetch("/api/town/quest", { headers: headers() });
      if (!resp.ok) return;
      const q = await resp.json();
      questTitle.textContent = q.title || "暂无目标";
      questDesc.textContent = q.description || "";
      const progress = clamp(q.progress ?? 0);
      questProgressFill.style.width = `${progress}%`;
      questProgressText.textContent = `${progress}% · ${q.winCondition || ""}`;
      questStatus.textContent = questStatusLabel(q.status);
      questTip.textContent = q.tip || "";
    } catch (_) {}
  }

  async function resetQuest() {
    try {
      await postJson("/api/town/quest/reset");
      await refreshQuest();
      await refreshEvents();
      setOpStatus("周目标已重置");
      playSoftBeep();
    } catch (e) {
      setOpStatus(e.message, true);
    }
  }

  function renderReplayCard(ev) {
    if (!ev) {
      replayCard.innerHTML = "<div class='line'>暂无事件</div>";
      replayPosLabel.textContent = "拖动滑块回顾";
      return;
    }
    replayCard.innerHTML = `
      <div class="row between" style="margin-bottom:8px;">
        <span class="timeline-type type-${escapeHtml(ev.type || "LIFE")}">${escapeHtml(ev.type || "-")}</span>
        <span class="line">${ev.createdAt ? new Date(ev.createdAt).toLocaleString() : ""}</span>
      </div>
      <strong>${escapeHtml(ev.title || "")}</strong>
      <div class="line" style="margin-top:8px;">${escapeHtml(ev.detail || "")}</div>
      ${ev.severity ? `<div class="line">标签：${escapeHtml(ev.severity)}</div>` : ""}`;
    replayPosLabel.textContent = `${replayIndex + 1} / ${replayEvents.length}`;
  }

  function renderReplayRail() {
    replayRail.innerHTML = replayEvents.map((ev, idx) =>
      `<button type="button" class="replay-dot ${escapeHtml(ev.type || "")}${idx === replayIndex ? " active" : ""}"
        data-idx="${idx}" title="${escapeHtml(ev.title || "")}"></button>`
    ).join("");
    replayRail.querySelectorAll(".replay-dot").forEach((dot) => {
      dot.addEventListener("click", () => {
        replayIndex = Number(dot.getAttribute("data-idx")) || 0;
        replaySlider.value = String(replayIndex);
        renderReplayCard(replayEvents[replayIndex]);
        renderReplayRail();
      });
    });
  }

  function setReplayIndex(idx) {
    if (!replayEvents.length) {
      replayIndex = 0;
      replaySlider.min = "0";
      replaySlider.max = "0";
      replaySlider.value = "0";
      renderReplayCard(null);
      renderReplayRail();
      return;
    }
    replayIndex = Math.max(0, Math.min(replayEvents.length - 1, idx));
    replaySlider.min = "0";
    replaySlider.max = String(replayEvents.length - 1);
    replaySlider.value = String(replayIndex);
    renderReplayCard(replayEvents[replayIndex]);
    renderReplayRail();
  }

  async function refreshReplay() {
    try {
      const resp = await fetch("/api/town/replay?limit=60", { headers: headers() });
      if (!resp.ok) return;
      const data = await resp.json();
      replayEvents = Array.isArray(data.events) ? data.events : [];
      if (replayIndex >= replayEvents.length) replayIndex = Math.max(0, replayEvents.length - 1);
      setReplayIndex(replayIndex);
    } catch (_) {}
  }

  async function refreshAiMetrics() {
    try {
      const resp = await fetch("/api/town/metrics/ai", { headers: headers() });
      if (!resp.ok) return;
      const data = await resp.json();
      aiTotal.textContent = String(data.totalCalls ?? 0);
      aiFailRate.textContent = `${Number(data.failRate ?? 0).toFixed(1)}%`;
      aiAvg.textContent = `${data.avgLatencyMs ?? 0}ms`;
      aiMetricsUpdated.textContent = data.updatedAt
        ? `更新于 ${new Date(data.updatedAt).toLocaleTimeString()}`
        : "";
      const recent = Array.isArray(data.recent) ? data.recent : [];
      aiRecent.innerHTML = recent.length
        ? recent.map((r) => `
          <div class="insight-row">
            <div class="row between">
              <strong>${escapeHtml(r.npcName || "-")}</strong>
              <span class="pill">${escapeHtml(r.status || "-")} · ${r.latencyMs ?? 0}ms</span>
            </div>
            <div class="line">${escapeHtml(r.kind || "")} · ${escapeHtml(r.detail || "")}</div>
            <div class="line">${r.at ? new Date(r.at).toLocaleString() : ""}</div>
          </div>`).join("")
        : "<div class='line'>暂无 AI 调用记录</div>";
    } catch (_) {}
  }

  async function refreshDecisionInsights() {
    try {
      const resp = await fetch("/api/town/decision/insights", { headers: headers() });
      if (!resp.ok) return;
      const data = await resp.json();
      const summary = data.summary || {};
      decisionSummary.textContent = `AI ${summary.aiCount ?? 0} · 规则 ${summary.ruleCount ?? 0} · 共 ${summary.total ?? 0}`;
      const world = data.worldModifiers || {};
      worldTagPill.textContent = world.tag || "NONE";
      worldModList.innerHTML = `
        <li>社交概率 × ${Number(world.socialChanceMultiplier ?? 1).toFixed(2)}</li>
        <li>能量消耗 × ${Number(world.energyDrainMultiplier ?? 1).toFixed(2)}</li>
        <li>投资偏置 ${Number(world.investReturnBias ?? 0).toFixed(2)}</li>
        <li>广播：${escapeHtml(world.broadcast || "-")}</li>`;
      const rows = Array.isArray(data.npcs) ? data.npcs : [];
      if (!rows.length) {
        decisionTable.innerHTML = "<div class='line'>暂无决策记录</div>";
        return;
      }
      decisionTable.innerHTML = `
        <div class="decision-head">
          <span>居民</span><span>决策</span><span>来源</span><span>理由</span>
        </div>
        ${rows.map((r) => `
          <div class="decision-row">
            <div><strong>${escapeHtml(r.name || "-")}</strong><div class="line">${escapeHtml(r.occupation || "")} @ ${escapeHtml(r.location || "")}</div></div>
            <div>${escapeHtml(r.decision || "-")}</div>
            <div>${escapeHtml(r.source || "-")}</div>
            <div class="line">${escapeHtml(sanitizeDecisionReason(r.reason || ""))}</div>
          </div>`).join("")}`;
    } catch (_) {}
  }

  async function refreshInsights() {
    await Promise.all([refreshAiMetrics(), refreshDecisionInsights()]);
  }

  async function refreshSaves() {
    try {
      const resp = await fetch("/api/town/saves", { headers: headers() });
      if (!resp.ok) return;
      const data = await resp.json();
      const slots = Array.isArray(data.slots) ? data.slots : [];
      if (!slots.length) {
        saveSlots.innerHTML = "<div class='line'>暂无命名存档</div>";
        return;
      }
      saveSlots.innerHTML = slots.map((s) => {
        const name = escapeHtml(s.name || "");
        return `
          <div class="save-slot">
            <div>
              <strong>${name}</strong>
              <div class="line">${s.updatedAt ? new Date(s.updatedAt).toLocaleString() : ""} · ${Math.round((s.size || 0) / 1024)} KB</div>
            </div>
            <div class="row">
              <button type="button" class="secondary" data-load="${name}">加载</button>
              <button type="button" class="danger" data-delete="${name}">删除</button>
            </div>
          </div>`;
      }).join("");
      saveSlots.querySelectorAll("[data-load]").forEach((btn) => {
        btn.addEventListener("click", () => loadSaveSlot(btn.getAttribute("data-load")));
      });
      saveSlots.querySelectorAll("[data-delete]").forEach((btn) => {
        btn.addEventListener("click", () => deleteSaveSlot(btn.getAttribute("data-delete")));
      });
    } catch (_) {}
  }

  async function saveNamedSlot() {
    const name = saveNameInput.value.trim();
    if (!name) {
      setOpStatus("请输入存档名", true);
      return;
    }
    try {
      const data = await postJson("/api/town/saves", { name });
      setOpStatus(data.message || "存档成功");
      saveNameInput.value = "";
      await refreshSaves();
    } catch (e) {
      setOpStatus(e.message, true);
    }
  }

  async function loadSaveSlot(name) {
    if (!name) return;
    if (!window.confirm(`加载存档「${name}」将覆盖当前世界，继续？`)) return;
    try {
      const data = await postJson(`/api/town/saves/${encodeURIComponent(name)}/load`);
      setOpStatus(`${data.message || "加载成功"}：NPC ${data.npcCount ?? "-"}，关系 ${data.relationshipCount ?? "-"}`);
      await refreshAll();
    } catch (e) {
      setOpStatus(e.message, true);
    }
  }

  async function deleteSaveSlot(name) {
    if (!name) return;
    if (!window.confirm(`确定删除存档「${name}」？`)) return;
    try {
      const data = await deleteJson(`/api/town/saves/${encodeURIComponent(name)}`);
      setOpStatus(data.message || "已删除");
      await refreshSaves();
    } catch (e) {
      setOpStatus(e.message, true);
    }
  }

  async function refreshDailyReport() {
    try {
      const resp = await fetch("/api/town/report/daily", { headers: headers() });
      if (!resp.ok) return;
      dailyReportCache = await resp.json();
    } catch (_) {}
  }

  function drawDailyPoster(report) {
    if (!dailyPoster || !report) return;
    const ctx = dailyPoster.getContext("2d");
    const w = dailyPoster.width;
    const h = dailyPoster.height;
    const grad = ctx.createLinearGradient(0, 0, w, h);
    grad.addColorStop(0, "#0a0e17");
    grad.addColorStop(0.5, "#121a2b");
    grad.addColorStop(1, "#0d1524");
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, w, h);
    ctx.strokeStyle = "rgba(53,224,208,0.35)";
    ctx.lineWidth = 3;
    ctx.strokeRect(40, 40, w - 80, h - 80);
    ctx.fillStyle = "#35e0d0";
    ctx.font = "bold 72px 'Segoe UI', sans-serif";
    ctx.fillText("CYBER TOWN", 80, 140);
    ctx.fillStyle = "#8ea4c9";
    ctx.font = "28px 'Segoe UI', sans-serif";
    const dateStr = report.generatedAt
      ? new Date(report.generatedAt).toLocaleDateString("zh-CN", { year: "numeric", month: "long", day: "numeric" })
      : new Date().toLocaleDateString("zh-CN");
    ctx.fillText(`赛博小镇日报 · ${dateStr}`, 80, 190);
    ctx.fillStyle = "#e8eef8";
    ctx.font = "36px 'Segoe UI', sans-serif";
    ctx.fillText(clipCanvasText(ctx, report.title || "赛博小镇日报", w - 160), 80, 260);
    const quest = report.quest || {};
    ctx.fillStyle = "#35e0d0";
    ctx.font = "bold 32px 'Segoe UI', sans-serif";
    ctx.fillText("本周目标", 80, 340);
    ctx.fillStyle = "#dce6f5";
    ctx.font="28px 'Segoe UI', sans-serif";
    ctx.fillText(clipCanvasText(ctx, quest.title || "-", w - 160), 80, 390);
    const progress = clamp(quest.progress ?? 0);
    ctx.fillStyle = "rgba(255,255,255,0.12)";
    ctx.fillRect(80, 410, w - 160, 18);
    ctx.fillStyle = "#35e0d0";
    ctx.fillRect(80, 410, (w - 160) * (progress / 100), 18);
    ctx.fillStyle = "#93a2c5";
    ctx.font = "22px 'Segoe UI', sans-serif";
    ctx.fillText(`${progress}% · ${questStatusLabel(quest.status)}`, 80, 460);
    ctx.fillStyle = "#35e0d0";
    ctx.font = "bold 32px 'Segoe UI', sans-serif";
    ctx.fillText("头条事件", 80, 540);
    const headlines = Array.isArray(report.headlines) ? report.headlines : [];
    ctx.fillStyle = "#c8d4ea";
    ctx.font = "24px 'Segoe UI', sans-serif";
    let y = 590;
    if (!headlines.length) {
      ctx.fillText("今日暂无重大事件", 80, y);
      y += 40;
    } else {
      headlines.slice(0, 6).forEach((line, i) => {
        ctx.fillText(`${i + 1}. ${clipCanvasText(ctx, line, w - 200)}`, 80, y);
        y += 44;
      });
    }
    ctx.fillStyle = "#35e0d0";
    ctx.font = "bold 28px 'Segoe UI', sans-serif";
    ctx.fillText("世界广播", 80, y + 30);
    ctx.fillStyle = "#aebcd6";
    ctx.font = "22px 'Segoe UI', sans-serif";
    wrapCanvasText(ctx, report.broadcast || "平静的一天", 80, y + 75, w - 160, 32);
    ctx.fillStyle = "#6b849f";
    ctx.font = "20px 'Segoe UI', sans-serif";
    ctx.fillText(`居民 ${report.npcCount ?? "-"} · 世界标签 ${report.worldTag || "NONE"}`, 80, h - 100);
    ctx.fillStyle = "rgba(53,224,208,0.5)";
    ctx.font = "18px 'Segoe UI', sans-serif";
    ctx.fillText("Generated by Cyber Town Runtime", 80, h - 60);
  }

  function clipCanvasText(ctx, text, maxWidth) {
    const s = String(text ?? "");
    if (ctx.measureText(s).width <= maxWidth) return s;
    let out = s;
    while (out.length > 1 && ctx.measureText(out + "…").width > maxWidth) {
      out = out.slice(0, -1);
    }
    return out + "…";
  }

  function wrapCanvasText(ctx, text, x, y, maxWidth, lineHeight) {
    const words = String(text ?? "").split("");
    let line = "";
    let cy = y;
    for (let i = 0; i < words.length; i++) {
      const test = line + words[i];
      if (ctx.measureText(test).width > maxWidth && line) {
        ctx.fillText(line, x, cy);
        line = words[i];
        cy += lineHeight;
      } else {
        line = test;
      }
    }
    if (line) ctx.fillText(line, x, cy);
  }

  async function exportDailyPoster() {
    try {
      if (!dailyReportCache) await refreshDailyReport();
      if (!dailyReportCache) throw new Error("无法获取日报数据");
      drawDailyPoster(dailyReportCache);
      dailyPoster.toBlob((blob) => {
        if (!blob) return;
        const a = document.createElement("a");
        a.href = URL.createObjectURL(blob);
        a.download = `cybertown-daily-${Date.now()}.png`;
        a.click();
        URL.revokeObjectURL(a.href);
        setOpStatus("日报海报已导出");
      }, "image/png");
    } catch (e) {
      setOpStatus(e.message, true);
    }
  }

  async function talkToNpcFromModal() {
    const id = selectedTalkNpcId;
    const message = talkMessageInput?.value?.trim();
    if (!message) {
      talkModalStatus.textContent = "请输入对话内容";
      return;
    }
    const npc = currentNpcMap.get(id);
    appendMessageBubble("me", message, "你");
    const npcBubble = appendMessageBubble("npc", "正在输入...", npc?.name || "NPC", true);
    talkModalStatus.textContent = "流式回复中...";
    talkMessageInput.value = "";
    if (talkStreamSource) {
      talkStreamSource.close();
      talkStreamSource = null;
    }
    try {
      const url = `/api/town/npc/${id}/talk/stream?message=${encodeURIComponent(message)}&clientKey=${encodeURIComponent(CLIENT_KEY)}`;
      talkStreamSource = new EventSource(url);
      let responseText = "";
      talkStreamSource.addEventListener("chunk", (ev) => {
        try {
          const payload = JSON.parse(ev.data);
          responseText += payload.text || "";
          npcBubble.classList.remove("typing");
          npcBubble.textContent = responseText || "...";
          chatList.scrollTop = chatList.scrollHeight;
        } catch (_) {}
      });
      talkStreamSource.addEventListener("done", (ev) => {
        const data = JSON.parse(ev.data);
        const tip = data.influenceActive
          ? `（影响强度${data.influenceWeight}，约${data.influenceDurationMinutes}分钟）`
          : "（短期无明显行为影响）";
        npcBubble.textContent = `${responseText || data.response || ""} ${tip}`.trim();
        if (data.context) {
          const npcInfo = currentNpcMap.get(id) || {};
          npcInfo.dialogueMemory = data.context;
          currentNpcMap.set(id, npcInfo);
        }
        if (data.mode) applyModeUi(data);
        talkModalStatus.textContent = "发送成功";
        talkStreamSource.close();
        talkStreamSource = null;
      });
      talkStreamSource.addEventListener("error", (ev) => {
        npcBubble.classList.remove("typing");
        try {
          if (ev.data) {
            const payload = JSON.parse(ev.data);
            if (payload.message) {
              npcBubble.textContent = payload.message;
              talkModalStatus.textContent = payload.message;
              talkStreamSource.close();
              talkStreamSource = null;
              return;
            }
          }
        } catch (_) {}
        if (!responseText) npcBubble.textContent = "对话失败，请稍后重试";
        talkModalStatus.textContent = "对话失败";
        if (talkStreamSource) {
          talkStreamSource.close();
          talkStreamSource = null;
        }
      });
    } catch (e) {
      npcBubble.classList.remove("typing");
      npcBubble.textContent = "对话失败，请稍后重试";
      talkModalStatus.textContent = e.message;
    }
  }

  async function refreshWorldNews() {
    try {
      const resp = await fetch("/api/town/world/news", { headers: headers() });
      if (!resp.ok) return;
      const data = await resp.json();
      const headlines = Array.isArray(data.headlines) ? data.headlines : [];
      newsList.innerHTML = headlines.map((h) => `<li>${escapeHtml(h)}</li>`).join("") || "<li>暂无新闻</li>";
      newsBrief.textContent = data.brief || "暂无摘要";
      newsUpdatedAt.textContent = data.updatedAt ? `更新于 ${new Date(data.updatedAt).toLocaleTimeString()}` : "";
    } catch (_) {}
  }

  async function popThoughtBubbles() {
    try {
      const resp = await fetch("/api/town/npcs/thought-bubbles", { headers: headers() });
      if (!resp.ok) return;
      const data = await resp.json();
      const bubbles = Array.isArray(data.bubbles) ? data.bubbles : [];
      for (const b of bubbles) {
        const node = document.createElement("div");
        node.className = "bubble";
        node.style.left = `${5 + Math.random() * 80}%`;
        node.style.top = `${10 + Math.random() * 65}%`;
        node.innerHTML = `<strong>${escapeHtml(b.npcName || "NPC")}</strong>：${escapeHtml(b.text || "")}`;
        thoughtLayer.appendChild(node);
        setTimeout(() => node.remove(), 9000);
      }
    } catch (_) {}
  }

  function scheduleThoughtBubbles() {
    const nextMs = 8000 + Math.floor(Math.random() * 9000);
    setTimeout(async () => {
      await popThoughtBubbles();
      scheduleThoughtBubbles();
    }, nextMs);
  }

  async function applyGodCommand() {
    if (isSpectator()) {
      godStatus.textContent = "旁观模式不可使用上帝指令";
      return;
    }
    const id = godNpcSelect.value;
    const instruction = godInstruction.value.trim();
    if (!id || !instruction) {
      godStatus.textContent = "请选择NPC并输入指令";
      return;
    }
    godStatus.textContent = "上帝指令执行中...";
    godNpcReply.textContent = "";
    try {
      const resp = await fetch(`/api/town/npc/${id}/god-command`, {
        method: "POST",
        headers: headers(true),
        body: JSON.stringify({ instruction })
      });
      const data = await resp.json();
      if (!resp.ok) throw new Error(data.message || "上帝指令执行失败");
      godStatus.textContent = data.message || "执行成功";
      if (data.npcReply) godNpcReply.textContent = `${data.npcName || "NPC"}：${data.npcReply}`;
      if (data.relationshipChange) {
        godNpcReply.textContent += ` ｜ 关系：${data.relationshipChange}`;
      }
      applyModeUi(data);
      godInstruction.value = "";
      await refreshSummary();
      await refreshEvents();
      await refreshRelationshipGraph();
    } catch (e) {
      godStatus.textContent = e.message;
    }
  }

  async function runBroadcast() {
    setOpStatus("正在手动广播...");
    try {
      const msg = await postJson("/api/town/broadcast/status");
      setOpStatus(typeof msg === "string" ? msg : "广播完成");
    } catch (e) {
      setOpStatus(e.message, true);
    }
  }

  async function endSimulationAndBack() {
    setOpStatus("正在结束模拟并清空数据...");
    try {
      const data = await postJson("/api/town/simulation/end");
      setOpStatus(`${data.message}（已清空 ${data.deletedCount} 人）`);
      setTimeout(() => { window.location.href = "/"; }, 400);
    } catch (e) {
      setOpStatus(e.message, true);
    }
  }

  async function exportSnapshot() {
    try {
      const resp = await fetch("/api/town/snapshot", { headers: headers() });
      if (!resp.ok) throw new Error("导出失败");
      const data = await resp.json();
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = `cybertown-snapshot-${Date.now()}.json`;
      a.click();
      URL.revokeObjectURL(a.href);
      setOpStatus("快照已导出");
    } catch (e) {
      setOpStatus(e.message, true);
    }
  }

  async function importSnapshotFile(file) {
    if (!file) return;
    try {
      const text = await file.text();
      const body = JSON.parse(text);
      const data = await postJson("/api/town/snapshot/import", body);
      setOpStatus(`${data.message}：NPC ${data.npcCount}，关系 ${data.relationshipCount}`);
      await refreshAll();
    } catch (e) {
      setOpStatus(e.message, true);
    }
  }

  function switchTab(tabId) {
    activeTab = tabId;
    mainTabs.querySelectorAll(".tab").forEach((btn) => {
      btn.classList.toggle("active", btn.dataset.tab === tabId);
    });
    document.querySelectorAll(".tab-panel").forEach((panel) => {
      panel.classList.toggle("active", panel.id === `tab-${tabId}`);
    });
    playSoftBeep();
    refreshTabData(tabId);
  }

  function refreshTabData(tabId) {
    if (tabId === "overview") {
      refreshQuest();
      refreshEvents();
      refreshDailyReport();
    } else if (tabId === "map") {
      refreshRelationshipGraph();
      refreshLocations();
    } else if (tabId === "replay") {
      refreshReplay();
    } else if (tabId === "insights") {
      refreshInsights();
    } else if (tabId === "saves") {
      refreshSaves();
      refreshMode();
    }
  }

  async function refreshAll() {
    await refreshSummary().catch((err) => setOpStatus(err.message, true));
    await Promise.all([
      refreshWorldBroadcast(),
      refreshWorldNews(),
      refreshEvents(),
      refreshLocations(),
      refreshQuest(),
      refreshRelationshipGraph(),
      refreshReplay(),
      refreshInsights(),
      refreshSaves(),
      refreshDailyReport(),
      refreshMode()
    ]);
  }

  function toggleSound() {
    soundEnabled = !soundEnabled;
    localStorage.setItem("cybertown.sound", soundEnabled ? "on" : "off");
    updateSoundBtn();
    if (soundEnabled) playSoftBeep();
  }

  mainTabs.querySelectorAll(".tab").forEach((btn) => {
    btn.addEventListener("click", () => switchTab(btn.dataset.tab || "overview"));
  });
  showRelationsChk?.addEventListener("change", () => renderMap([...currentNpcMap.values()]));
  document.getElementById("resetQuestBtn").addEventListener("click", resetQuest);
  document.getElementById("exportDailyBtn").addEventListener("click", exportDailyPoster);
  replaySlider.addEventListener("input", () => setReplayIndex(Number(replaySlider.value)));
  document.getElementById("saveSlotBtn").addEventListener("click", saveNamedSlot);
  document.getElementById("refreshSavesBtn").addEventListener("click", () => refreshSaves());
  soundToggleBtn.addEventListener("click", toggleSound);
  document.getElementById("broadcastBtn").addEventListener("click", runBroadcast);
  document.getElementById("reloadBtn").addEventListener("click", () => refreshAll());
  document.getElementById("endSimBtn").addEventListener("click", endSimulationAndBack);
  document.getElementById("godApplyBtn").addEventListener("click", applyGodCommand);
  document.getElementById("closeTalkModalBtn").addEventListener("click", closeTalkModal);
  document.getElementById("sendTalkBtn").addEventListener("click", talkToNpcFromModal);
  document.getElementById("toggleModeBtn").addEventListener("click", toggleMode);
  document.getElementById("exportSnapshotBtn").addEventListener("click", exportSnapshot);
  document.getElementById("importSnapshotInput").addEventListener("change", (e) => {
    importSnapshotFile(e.target.files?.[0]);
    e.target.value = "";
  });
  talkMessageInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      talkToNpcFromModal();
    }
  });
  talkModalMask.addEventListener("click", (e) => { if (e.target === talkModalMask) closeTalkModal(); });
  document.getElementById("closeProfileModalBtn").addEventListener("click", closeProfileModal);
  profileModalMask.addEventListener("click", (e) => { if (e.target === profileModalMask) closeProfileModal(); });

  window.openTalkModal = openTalkModal;
  window.openProfileModal = openProfileModal;
  window.openRelationships = openRelationships;

  updateSoundBtn();
  refreshAll();
  connectSSE();
  scheduleThoughtBubbles();

  setInterval(() => {
    if (connectionStatus.textContent.includes("轮询")) {
      refreshSummary().catch(() => {});
      refreshEvents();
    }
  }, 5000);

  setInterval(refreshWorldBroadcast, 8000);
  setInterval(refreshWorldNews, 15000);
  setInterval(refreshEvents, 12000);

  setInterval(() => {
    refreshQuest();
    if (activeTab === "map") refreshRelationshipGraph();
    else if (activeTab === "replay") refreshReplay();
    else if (activeTab === "insights") refreshInsights();
    else if (activeTab === "saves") refreshSaves();
  }, 15000);
})();
