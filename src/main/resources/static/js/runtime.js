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
  const godPanel = document.getElementById("godPanel");

  let source = null;
  let talkStreamSource = null;
  let currentNpcMap = new Map();
  let selectedTalkNpcId = null;
  let currentMode = "operator";
  let locationCache = [];
  let selectedLocation = null;

  /** 固定城区布局（百分比），避免数据库坐标重叠导致地图乱 */
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

  function setOpStatus(text, isError = false) {
    opStatus.style.color = isError ? "#f87171" : "#93a2c5";
    opStatus.textContent = text;
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
          const reasonRaw = n.lastDecisionReason || "";
          const reason = /Expected BEGIN_OBJECT|IllegalStateException|AI失败:|Exception:/i.test(reasonRaw)
            ? "规则引擎决策（AI暂不可用）"
            : reasonRaw;
      const decision = n.lastDecision || n.action || n.currentAction || "-";
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
            <button class="link-btn" onclick="openProfileModal('${n.id}', 'assets')">资产详情</button>
            <button class="link-btn" onclick="openProfileModal('${n.id}', 'education')">学历详情</button>
            <button class="link-btn" onclick="openProfileModal('${n.id}', 'compensation')">工资详情</button>
            <button class="link-btn" onclick="openRelationships('${n.id}')">人际关系</button>
          </div>
          <div class="line">能量 ${energy}</div><div class="bar"><span style="width:${clamp(energy)}%"></span></div>
          <div class="line">饥饿 ${hunger}</div><div class="bar"><span style="width:${clamp(hunger)}%"></span></div>
          <div class="line">快乐 ${happiness}</div><div class="bar"><span style="width:${clamp(happiness)}%"></span></div>
          <div class="line">社交需求 ${socialNeed}</div><div class="bar"><span style="width:${clamp(socialNeed)}%"></span></div>
          <button class="tiny-btn operator-only ${isSpectator() ? "hidden" : ""}" onclick="openTalkModal('${n.id}')">打开对话</button>
        </div>`;
    }).join("");
    currentNpcMap = new Map(npcs.map((n) => [n.id, n]));
    syncGodNpcOptions(npcs);
    renderMap(npcs);
  }

  function clamp(v) { return Math.max(0, Math.min(100, Number(v) || 0)); }

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
    if (occupation.includes("程序员") || occupation.includes("黑客")) return "🧑‍💻";
    if (occupation.includes("警察") || occupation.includes("保安")) return "👮";
    if (occupation.includes("医生")) return "🧑‍⚕️";
    if (occupation.includes("设计")) return "🎨";
    if (occupation.includes("司机")) return "🚕";
    if (occupation.includes("舞者")) return "💃";
    if (occupation.includes("酒吧")) return "🍸";
    return "🤖";
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
      const resp = await fetch(`/api/town/npc/${id}/profile/extended`);
      if (!resp.ok) throw new Error("读取详情失败");
      const data = await resp.json();
      if (tab === "assets") {
        const a = data.assets || {};
        profileModalContent.innerHTML = `
          <ul class="detail-list">
            <li>现金：${Number(a.cash || 0).toFixed(2)} ${a.currency || "元"}</li>
            <li>储蓄：${Number(a.savings || 0).toFixed(2)} ${a.currency || "元"}</li>
            <li>负债：${Number(a.debt || 0).toFixed(2)} ${a.currency || "元"}</li>
            <li>总资产：${Number(a.totalAssets || 0).toFixed(2)} ${a.currency || "元"}</li>
            <li>净资产：${Number(a.netWorth || 0).toFixed(2)} ${a.currency || "元"}</li>
            <li>流动性比例：${Number(a.liquidityRatio || 0).toFixed(2)}%</li>
            <li>负债资产比：${Number(a.debtToAssetRatio || 0).toFixed(2)}%</li>
            <li>财务风险等级：${a.riskLevel || "未知"}</li>
          </ul>`;
      } else if (tab === "education") {
        const timeline = Array.isArray(data.educationTimeline) ? data.educationTimeline : [];
        const items = timeline.map((t) => `<li>${escapeHtml(t.stage)} · ${escapeHtml(t.school || "-")}<br><span class="line">${escapeHtml(t.focus || "")}</span></li>`).join("");
        profileModalContent.innerHTML = `
          <div class="line">当前学历：${escapeHtml(data.educationLevel || "-")}，从业时长：${data.workExperienceMonths || 0} 月</div>
          <ul class="detail-list">${items || "<li>暂无教育轨迹</li>"}</ul>`;
      } else {
        const c = data.compensation || {};
        const optionPart = c.hasStockOption
          ? `${Number(c.monthlyStockOption || 0).toFixed(2)} ${c.currency || "元"}`
          : "无（当前岗位/学历/公司不提供）";
        profileModalContent.innerHTML = `
          <ul class="detail-list">
            <li>月薪（现金部分）：${Number(c.monthlyCash || 0).toFixed(2)} ${c.currency || "元"}</li>
            <li>月度股票期权：${optionPart}</li>
            <li>月总收入：${Number(c.monthlyTotal || 0).toFixed(2)} ${c.currency || "元"}（约 ${c.monthlyDisplay || "-"}）</li>
            <li>年总收入：${Number(c.annualTotal || 0).toFixed(2)} ${c.currency || "元"}（约 ${c.annualDisplay || "-"}）</li>
            <li>收入等级：${c.payLevel || "未知"}</li>
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
    godNpcSelect.innerHTML = npcs.map((n) => `<option value="${n.id}">${n.name} (${n.occupation || "-"})</option>`).join("");
    if (currentValue) godNpcSelect.value = currentValue;
  }

  async function refreshSummary() {
    const [summaryResp, npcsResp] = await Promise.all([
      fetch("/api/town/stats/summary"),
      fetch("/api/town/npcs")
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
      const resp = await fetch("/api/town/world/broadcast");
      if (!resp.ok) return;
      const data = await resp.json();
      worldBroadcast.textContent = data.message || "暂无新的世界广播";
      worldBroadcastTime.textContent = "更新时间: " + new Date(data.timestamp || Date.now()).toLocaleTimeString()
        + (data.source ? ` · ${data.source}` : "");
    } catch (_) {}
  }

  async function refreshEvents() {
    try {
      const resp = await fetch("/api/town/events?limit=30");
      if (!resp.ok) return;
      const data = await resp.json();
      const events = Array.isArray(data.events) ? data.events : [];
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
      const resp = await fetch("/api/town/locations");
      if (!resp.ok) return;
      const data = await resp.json();
      locationCache = Array.isArray(data.locations) ? data.locations : [];
      renderMap([...currentNpcMap.values()]);
    } catch (_) {}
  }

  function renderMap(npcs) {
    if (!townMap) return;

    const byLoc = {};
    (npcs || []).forEach((n) => {
      const name = n.location || n.currentLocation || "未知";
      if (!byLoc[name]) byLoc[name] = [];
      byLoc[name].push(n);
    });

    // 合并：布局表 + API 地点 + NPC 实际出现过的地点
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

    const roadsSvg = MAP_ROADS.map(([a, b]) => {
      if (!points[a] || !points[b]) return "";
      return `<line x1="${points[a].x}%" y1="${points[a].y}%" x2="${points[b].x}%" y2="${points[b].y}%"
        stroke="rgba(90,140,200,0.28)" stroke-width="2" stroke-dasharray="6 8"/>`;
    }).join("");

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
      const resp = await fetch("/api/town/world/news");
      if (!resp.ok) return;
      const data = await resp.json();
      const headlines = Array.isArray(data.headlines) ? data.headlines : [];
      newsList.innerHTML = headlines.map((h) => `<li>${escapeHtml(h)}</li>`).join("") || "<li>暂无新闻</li>";
      newsBrief.textContent = data.brief || "暂无摘要";
      newsUpdatedAt.textContent = data.updatedAt ? `更新于 ${new Date(data.updatedAt).toLocaleTimeString()}` : "";
    } catch (_) {}
  }

  function escapeHtml(text) {
    return String(text ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;");
  }

  async function popThoughtBubbles() {
    try {
      const resp = await fetch("/api/town/npcs/thought-bubbles");
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
      await refreshSummary();
      await refreshEvents();
      await refreshLocations();
    } catch (e) {
      setOpStatus(e.message, true);
    }
  }

  document.getElementById("broadcastBtn").addEventListener("click", runBroadcast);
  document.getElementById("reloadBtn").addEventListener("click", () => refreshSummary().catch((err) => setOpStatus(err.message, true)));
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

  refreshMode();
  refreshSummary().catch((err) => setOpStatus(err.message, true));
  refreshWorldBroadcast();
  refreshWorldNews();
  refreshEvents();
  refreshLocations();
  connectSSE();
  setInterval(() => {
    if (connectionStatus.textContent.includes("轮询")) {
      refreshSummary().catch(() => {});
      refreshEvents();
    }
  }, 5000);
  setInterval(refreshWorldBroadcast, 8000);
  setInterval(refreshWorldNews, 15000);
  setInterval(refreshEvents, 12000);
  scheduleThoughtBubbles();
})();
