/**
 * Snake Arena: Clash of Serpents
 * Module: Main Game Controller & Lifecycle Orchestrator
 * --------------------------------------------------------------------------
 * Manages:
 * - Multi-window & tab session independence via sessionStorage and window.name
 * - Background Web Worker heartbeat ticker (100ms ticks + 1.5s background server ping)
 * - Game state: identity, snake lifecycle, controls locking, and HUD statistics
 * - Tactile Polish: Contextual in-game POST button labeling ("Deploy / Sync Ular")
 * - Keyboard (WASD/Arrows/Space) and Touch D-pad bindings
 */

window.SnakeArena = window.SnakeArena || {};

(function() {
    // Tab independence
    if (!window.name || !window.name.startsWith("WIN_")) {
        window.name = "WIN_" + Math.random().toString(36).substring(2, 9);
    }
    let tabInstanceKey = "snakeArenaPlayerId_" + window.name;
    let playerId = sessionStorage.getItem(tabInstanceKey);
    if (!playerId) {
        playerId = "PL-" + Math.floor(1000 + Math.random() * 9000);
        sessionStorage.setItem(tabInstanceKey, playerId);
    }

    let playerName = "";
    let selectedClass = "VIPER";
    let selectedColor = "#059669";
    let snakeId = null;
    let score = 0;
    let skillActive = false;
    let skillActiveUntil = 0;
    let skillCooldownUntil = 0;
    let skillTimerInterval = null;
    let gameRunning = false;

    // Web Worker ticker & direct background heartbeat
    const workerBlob = new Blob([`
        let count = 0;
        let currentSnakeId = null;
        let arenaUrl = '';

        setInterval(() => {
            count++;
            postMessage({ type: 'tick' });

            if (count % 15 === 0 && currentSnakeId && arenaUrl) {
                fetch(arenaUrl + '/api/v1/arena/snakes/' + currentSnakeId + '/heartbeat', {
                    method: 'PATCH',
                    headers: { 'Content-Type': 'application/json' }
                }).catch(() => {});
            }
        }, 100);

        onmessage = (e) => {
            if (!e.data) return;
            if (e.data.type === 'setSnake') {
                currentSnakeId = e.data.snakeId;
                arenaUrl = e.data.arenaUrl;
            } else if (e.data.type === 'clearSnake') {
                currentSnakeId = null;
            }
        };
    `], { type: 'application/javascript' });
    const backgroundWorker = new Worker(URL.createObjectURL(workerBlob));

    backgroundWorker.onmessage = (e) => {
        if (!e.data) return;
        if (e.data.type === 'tick' || e.data === 'tick') {
            if (window.fetchArenaState) {
                window.fetchArenaState();
            }
        }
    };

    function setPlayerIdentity(id, name) {
        playerId = id;
        playerName = name;
        if (id) {
            sessionStorage.setItem(tabInstanceKey, id);
        } else {
            sessionStorage.removeItem(tabInstanceKey);
        }
        window.playerId = id;
        window.playerName = name;
        if (window.updatePlayerIdBadge) {
            window.updatePlayerIdBadge(id);
        }
    }

    function setSnakeId(id) {
        snakeId = id;
        window.snakeId = id;
        if (id) {
            sessionStorage.setItem("activeSnakeId", id);
        } else {
            sessionStorage.removeItem("activeSnakeId");
        }
        if (backgroundWorker) {
            backgroundWorker.postMessage(id ? {
                type: 'setSnake',
                snakeId: id,
                arenaUrl: window.ARENA_SERVICE_URL || 'http://localhost:8082'
            } : { type: 'clearSnake' });
        }
    }

    function selectOverlayClass(className, color, el) {
        if (gameRunning) return;
        if (window.playSfx) window.playSfx('click');
        selectedClass = className;
        selectedColor = color;
        window.selectedClass = className;
        window.selectedColor = color;

        document.querySelectorAll('.overlay-class-opt').forEach(opt => {
            const optClass = opt.dataset.class || opt.querySelector('.opt-title')?.innerText.trim();
            opt.classList.toggle('active', (optClass || "").toUpperCase() === className.toUpperCase());
        });

        document.querySelectorAll('#sidebarClassSelector .class-card').forEach(c => {
            c.classList.toggle('selected', (c.dataset.class || "").toUpperCase() === className.toUpperCase());
        });

        const hudC = document.getElementById("hudClass");
        if (hudC) {
            hudC.innerText = className;
            hudC.style.color = color;
        }
    }

    function selectSidebarClass(className, color, fromUserClick = false) {
        if (gameRunning) return;
        if (fromUserClick && window.playSfx) window.playSfx('click');
        selectedClass = className;
        selectedColor = color;
        window.selectedClass = className;
        window.selectedColor = color;

        document.querySelectorAll('#sidebarClassSelector .class-card').forEach(c => {
            c.classList.toggle('selected', (c.dataset.class || "").toUpperCase() === className.toUpperCase());
        });

        document.querySelectorAll('.overlay-class-opt').forEach(opt => {
            const optClass = opt.dataset.class || opt.querySelector('.opt-title')?.innerText.trim();
            opt.classList.toggle('active', (optClass || "").toUpperCase() === className.toUpperCase());
        });

        const hudC = document.getElementById("hudClass");
        if (hudC) {
            hudC.innerText = className;
            hudC.style.color = color;
        }
    }

    function getDeviceProfile(baseName) {
        const cleanBase = (baseName || "Pilot").trim().replace(/#\d+$/, "");
        const sessionKey = "snake_tab_profile_" + (window.name || "main") + "_" + cleanBase.toLowerCase();
        let saved = sessionStorage.getItem(sessionKey);
        if (saved) {
            try {
                const parsed = JSON.parse(saved);
                if (parsed && parsed.tag) return parsed;
            } catch (e) {}
        }

        // Hasilkan tag 4-digit baru yang tidak sedang aktif di arena
        const activeNames = (window.arenaSnakes || []).map(s => (s.playerName || "").toLowerCase());
        let newTag;
        for (let i = 0; i < 50; i++) {
            newTag = String(Math.floor(1000 + Math.random() * 9000));
            const candidate = `${cleanBase.toLowerCase()}#${newTag}`;
            if (!activeNames.includes(candidate)) {
                break;
            }
        }
        const profile = { baseName: cleanBase, tag: newTag, playerId: null };
        sessionStorage.setItem(sessionKey, JSON.stringify(profile));
        return profile;
    }

    function saveDeviceProfile(baseName, tag, pId) {
        const cleanBase = (baseName || "Pilot").trim().replace(/#\d+$/, "");
        const sessionKey = "snake_tab_profile_" + (window.name || "main") + "_" + cleanBase.toLowerCase();
        const profile = { baseName: cleanBase, tag: tag, playerId: pId };
        sessionStorage.setItem(sessionKey, JSON.stringify(profile));
    }

    function updateTagBadges(nameVal) {
        const cleanBase = (nameVal || "").trim().replace(/#\d+$/, "");
        if (!cleanBase) {
            const elO = document.getElementById("overlayPlayerTag");
            if (elO) elO.innerText = "TAG: #????";
            const elSide = document.getElementById("sidebarPlayerTag");
            if (elSide) elSide.innerText = "TAG: #????";
            return null;
        }
        const explicitTagMatch = nameVal.match(/#(\d{4})$/);
        const tag = explicitTagMatch ? explicitTagMatch[1] : getDeviceProfile(cleanBase).tag;
        const elO = document.getElementById("overlayPlayerTag");
        if (elO) elO.innerText = `TAG: #${tag}`;
        const elSide = document.getElementById("sidebarPlayerTag");
        if (elSide) elSide.innerText = `TAG: #${tag}`;
        return tag;
    }

    async function deployCurrentSnake(callsign, sClass, sColor) {
        const joinPayload = {
            playerId: window.registeredPlayerId || playerId,
            playerName: callsign,
            snakeType: sClass,
            color: sColor
        };
        const res = await window.makeApiCall("POST", `${window.ARENA_SERVICE_URL || 'http://localhost:8082'}/api/v1/arena/snakes`, joinPayload);
        if (res && res.success && res.data && res.data.playerName) {
            playerName = res.data.playerName;
            window.playerName = res.data.playerName;
        }
        return res;
    }

    async function validateAndStartGame() {
        if (window.playSfx) window.playSfx('click');
        const input = document.getElementById("overlayPlayerName");
        const val = input ? input.value.trim() : "";
        const valMsg = document.getElementById("nameValidationMsg");

        if (!val) {
            if (input) {
                input.classList.add("error");
                input.focus();
            }
            if (valMsg) {
                valMsg.innerText = "[ERR_REQUIRED] Nama pemain wajib diisi sebelum bermain!";
                valMsg.style.display = "block";
            }
            return;
        }

        const cleanBase = val.replace(/#\d+$/, "").trim();
        const tagMatch = val.match(/#(\d{4})$/);
        let tag = tagMatch ? tagMatch[1] : getDeviceProfile(cleanBase).tag;
        let fullCallsign = `${cleanBase}#${tag}`;

        const startBtn = document.querySelector(".btn-deploy");
        const originalText = startBtn ? startBtn.innerText : "MASUK KE ARENA";
        if (startBtn) {
            startBtn.innerText = "CONNECTING LINK...";
            startBtn.disabled = true;
        }

        // Register or retrieve player from Player Service using unique tag
        let targetId = null;
        try {
            const devProfile = getDeviceProfile(cleanBase);
            if (devProfile && devProfile.playerId) {
                // Verifikasi apakah akun pada device ini masih ada di backend
                const checkRes = await window.makeApiCall("GET", `${window.PLAYER_SERVICE_URL || 'http://localhost:8081'}/api/v1/players/${devProfile.playerId}`, null, true);
                if (checkRes && checkRes.success && checkRes.data) {
                    targetId = devProfile.playerId;
                    fullCallsign = checkRes.data.username;
                }
            }

            if (!targetId) {
                let regRes = await window.makeApiCall("POST", `${window.PLAYER_SERVICE_URL || 'http://localhost:8081'}/api/v1/players`, { username: fullCallsign, snakeType: selectedClass });
                if (regRes && regRes.success && regRes.data && regRes.data.id) {
                    targetId = regRes.data.id;
                    fullCallsign = regRes.data.username;
                    saveDeviceProfile(cleanBase, tag, targetId);
                } else {
                    // Jika terjadi tabrakan tag yang langka, coba tag baru
                    for (let attempt = 0; attempt < 5; attempt++) {
                        tag = String(Math.floor(1000 + Math.random() * 9000));
                        fullCallsign = `${cleanBase}#${tag}`;
                        regRes = await window.makeApiCall("POST", `${window.PLAYER_SERVICE_URL || 'http://localhost:8081'}/api/v1/players`, { username: fullCallsign, snakeType: selectedClass });
                        if (regRes && regRes.success && regRes.data && regRes.data.id) {
                            targetId = regRes.data.id;
                            fullCallsign = regRes.data.username;
                            saveDeviceProfile(cleanBase, tag, targetId);
                            break;
                        }
                    }
                }
            }

            if (targetId) {
                if (window.updatePlayerIdBadge) window.updatePlayerIdBadge(targetId);
                setPlayerIdentity(targetId, fullCallsign);
            }
        } catch (err) {}

        const res = await deployCurrentSnake(fullCallsign, selectedClass, selectedColor);
        if (startBtn) {
            startBtn.innerText = originalText;
            startBtn.disabled = false;
        }

        if (!res.success) {
            if (input) input.classList.add("error");
            const errMsg = (res.data && res.data.detail) ? res.data.detail : `Callsign '${fullCallsign}' sudah aktif di arena. Silakan pilih callsign lain.`;
            if (valMsg) {
                valMsg.innerText = `[ERR_DUPLICATE_CALLSIGN] ${errMsg}`;
                valMsg.style.display = "block";
            }
            if (input) {
                input.select();
                input.focus();
            }
            alert(`[ERR_DUPLICATE_CALLSIGN]\n${errMsg}`);
            return;
        }

        if (input) input.classList.remove("error");
        if (valMsg) valMsg.style.display = "none";
        playerName = fullCallsign;
        window.playerName = fullCallsign;

        updateTagBadges(cleanBase);

        const inpS = document.getElementById("inputPlayerName");
        if (inpS) inpS.value = cleanBase;
        const inpO = document.getElementById("overlayPlayerName");
        if (inpO) inpO.value = cleanBase;

        const hudP = document.getElementById("hudPlayer");
        if (hudP) {
            hudP.innerText = fullCallsign;
            hudP.style.color = "#ffffff";
        }
        const hudC = document.getElementById("hudClass");
        if (hudC) {
            hudC.innerText = selectedClass;
            hudC.style.color = selectedColor;
        }

        const overlay = document.getElementById("gameOverlay");
        if (overlay) overlay.classList.add("hidden");

        gameRunning = true;
        window.gameRunning = true;
        score = 0;
        window.score = 0;
        window.prevScore = 0;
        const hudS = document.getElementById("hudScore");
        if (hudS) hudS.innerText = "0";
        lockInGameControls(true);

        if (res.data && res.data.id) {
            snakeId = res.data.id;
            window.snakeId = snakeId;
            sessionStorage.setItem("activeSnakeId", snakeId);
            if (backgroundWorker) {
                backgroundWorker.postMessage({
                    type: 'setSnake',
                    snakeId: snakeId,
                    arenaUrl: window.ARENA_SERVICE_URL || 'http://localhost:8082'
                });
            }
        }
        if (window.fetchArenaState) await window.fetchArenaState();
    }

    /**
     * Locks or unlocks in-game controls and switches REST button labels
     * Tactile Polish: Displays "Deploy / Sync Ular" when in-game
     */
    function lockInGameControls(locked) {
        const inpS = document.getElementById("inputPlayerName");
        if (inpS) inpS.disabled = locked;

        document.querySelectorAll('#sidebarClassSelector .class-card').forEach(c => {
            c.classList.toggle('locked', locked);
        });

        // Tactile Polish Item 1: Clear In-Game POST labeling
        const elPost = document.getElementById("labelBtnPost");
        if (elPost) elPost.innerText = locked ? "Deploy / Sync Ular" : "Daftar Akun (Lobby)";

        const elGet = document.getElementById("labelBtnGet");
        if (elGet) elGet.innerText = locked ? "Telemetri Ular (GET)" : "Ambil Profil (Lobby)";

        const elPut = document.getElementById("labelBtnPut");
        if (elPut) elPut.innerText = locked ? "Ganti Skin Live (PUT)" : "Update Akun (Lobby)";

        const elPatch = document.getElementById("labelBtnPatch");
        if (elPatch) elPatch.innerText = locked ? "Makan Apel (PATCH)" : "Patch Stats (Lobby)";

        const elDel = document.getElementById("labelBtnDelete");
        if (elDel) elDel.innerText = locked ? "Keluar Arena (DELETE)" : "Hapus Akun (Lobby)";

        const elSub = document.getElementById("apiModuleSubtitle");
        if (elSub) elSub.innerText = locked ? "(ARENA SERVICE)" : "(PLAYER SERVICE)";
    }

    function endGame(reason) {
        if (window.playSfx) window.playSfx('death');
        gameRunning = false;
        window.gameRunning = false;
        skillActive = false;
        window.skillActive = false;
        skillActiveUntil = 0;
        skillCooldownUntil = 0;
        if (skillTimerInterval) { clearInterval(skillTimerInterval); skillTimerInterval = null; }
        updateSkillStatusUi("[READY]", "var(--tactical-emerald)");
        sessionStorage.removeItem("activeSnakeId");
        if (backgroundWorker) {
            backgroundWorker.postMessage({ type: 'clearSnake' });
        }
        snakeId = null;
        window.snakeId = null;
        lockInGameControls(false);

        const overlay = document.getElementById("gameOverlay");
        if (overlay) overlay.classList.remove("hidden");
        const oTitle = document.getElementById("overlayTitle");
        if (oTitle) {
            oTitle.innerText = "STATUS: HULL BREACH // ELIMINATED";
            oTitle.style.color = "var(--crimson-hazard)";
        }
        const oSub = document.getElementById("overlaySubtitle");
        if (oSub) {
            oSub.innerHTML = `
                <span style="color:var(--crimson-hazard); font-weight:700;">${reason || "Serpent mengalami impak fatal!"}</span><br>
                Skor akhir: <b>${score} pts</b>. Konfigurasi ulang callsign atau chassis untuk re-deployment.
            `;
        }
        const deployBtn = document.querySelector(".btn-deploy");
        if (deployBtn) deployBtn.innerText = "RE-MASUK KE ARENA";
    }

    function changeDir(dx, dy) {
        if (dx === 0 && dy === -1 && window.sendDirection) window.sendDirection("UP");
        if (dx === 0 && dy === 1 && window.sendDirection) window.sendDirection("DOWN");
        if (dx === -1 && dy === 0 && window.sendDirection) window.sendDirection("LEFT");
        if (dx === 1 && dy === 0 && window.sendDirection) window.sendDirection("RIGHT");
    }

    async function triggerSkill() {
        const activeId = window.snakeId || snakeId || sessionStorage.getItem("activeSnakeId");
        if (!activeId || !gameRunning) return;
        const now = Date.now();

        if (skillActive) return;

        if (now < skillCooldownUntil) {
            const sec = Math.ceil((skillCooldownUntil - now) / 1000);
            updateSkillStatusUi(`[RECHARGE: ${sec}s]`, "var(--solar-amber)");
            return;
        }

        // Verify that activeId is alive in arena state, or auto-heal to matching snake
        let targetId = activeId;
        if (window.arenaSnakes && window.arenaSnakes.length > 0) {
            const current = window.arenaSnakes.find(s => s.id === activeId);
            if (!current) {
                const byName = window.arenaSnakes.find(s => s.playerName && s.playerName === window.playerName && s.alive);
                if (byName) {
                    targetId = byName.id;
                    setSnakeId(byName.id);
                }
            }
        }

        const url = `${window.ARENA_SERVICE_URL || 'http://localhost:8082'}/api/v1/arena/snakes/${targetId}/skill?active=true`;
        if (window.makeApiCall) {
            const res = await window.makeApiCall("PATCH", url);
            if (!res.success) {
                if (res.status === 404) {
                    console.warn("[triggerSkill] Snake not found on arena server (404).");
                    updateSkillStatusUi("[NOT IN ARENA]", "var(--crimson-hazard)");
                    setTimeout(() => updateSkillStatusUi("[READY]", "var(--tactical-emerald)"), 2000);
                    return;
                }
            }
        }

        if (window.playSfx) window.playSfx('skill');
        skillActive = true;
        window.skillActive = true;
        skillActiveUntil = now + 3000;
        skillCooldownUntil = now + 3000 + 5000;
        updateSkillStatusUi("[ACTIVE: 3s]", "var(--tactical-emerald)");

        if (skillTimerInterval) clearInterval(skillTimerInterval);
        skillTimerInterval = setInterval(() => {
            const cur = Date.now();
            if (cur < skillActiveUntil) {
                const left = Math.ceil((skillActiveUntil - cur) / 1000);
                updateSkillStatusUi(`[ACTIVE: ${left}s]`, "var(--tactical-emerald)");
            } else if (cur < skillCooldownUntil) {
                if (skillActive) {
                    skillActive = false;
                    window.skillActive = false;
                    const curId = window.snakeId || snakeId || sessionStorage.getItem("activeSnakeId");
                    if (window.makeApiCall && curId) {
                        window.makeApiCall("PATCH", `${window.ARENA_SERVICE_URL || 'http://localhost:8082'}/api/v1/arena/snakes/${curId}/skill?active=false`, null, true);
                    }
                }
                const cdLeft = Math.ceil((skillCooldownUntil - cur) / 1000);
                updateSkillStatusUi(`[RECHARGE: ${cdLeft}s]`, "var(--solar-amber)");
            } else {
                skillActive = false;
                window.skillActive = false;
                clearInterval(skillTimerInterval);
                skillTimerInterval = null;
                updateSkillStatusUi("[READY]", "var(--tactical-emerald)");
            }
        }, 250);
    }

    function updateSkillStatusUi(text, color) {
        const el = document.getElementById("hudSkillStatus");
        if (el) {
            el.innerText = text;
            el.style.color = color;
        }
        const touchBtn = document.querySelector(".btn-skill-touch");
        if (touchBtn) {
            touchBtn.innerText = text.includes("ACTIVE") ? "ACTIVE" : (text.includes("RECHARGE") ? "CD" : "SKILL");
            touchBtn.style.opacity = text.includes("RECHARGE") ? "0.5" : "1";
        }
    }

    function renderLeaderboard(leaderboard) {
        const container = document.getElementById("leaderboardContainer") || document.getElementById("leaderboardList");
        if (!container) return;

        let entries = (leaderboard && leaderboard.length > 0) ? [...leaderboard] : [];
        if (entries.length === 0 && window.arenaSnakes && window.arenaSnakes.length > 0) {
            entries = window.arenaSnakes.map(s => ({
                snakeId: s.id,
                playerName: s.playerName,
                snakeType: s.snakeType,
                score: s.score || 0
            }));
        }

        if (entries.length === 0) {
            container.innerHTML = `<div class="lb-item" style="color:var(--text-dim); justify-content:center;">(Menunggu pemain join...)</div>`;
            return;
        }

        // Sort descending by score
        entries.sort((a, b) => (b.score || 0) - (a.score || 0));

        const curSnakeId = window.snakeId || snakeId || sessionStorage.getItem("activeSnakeId");

        container.innerHTML = entries.slice(0, 10).map((s, idx) => {
            const isUser = (s.snakeId === curSnakeId || s.id === curSnakeId || (window.playerName && s.playerName === window.playerName));
            const color = s.color || (s.snakeType === "VIPER" ? "var(--tactical-emerald)" : (s.snakeType === "PYTHON" ? "var(--cyber-cyan)" : "var(--solar-amber)"));
            const rankBadge = idx === 0 ? "🥇" : idx === 1 ? "🥈" : idx === 2 ? "🥉" : `#${idx + 1}`;
            return `
                <div class="lb-item ${isUser ? 'user-snake' : ''}">
                    <span style="display:flex; align-items:center; gap:6px;">
                        <b style="color:var(--solar-amber); min-width:20px;">${rankBadge}</b>
                        <span style="color:${color}; font-weight:700;">${s.playerName}${isUser ? ' (ANDA)' : ''}</span>
                    </span>
                    <span style="font-weight:700; color:#cbd5e1;">${s.score || 0} pts</span>
                </div>
            `;
        }).join("");
    }

    function cleanupCurrentSnake() {
        const current = snakeId || sessionStorage.getItem("activeSnakeId");
        if (current) {
            sessionStorage.removeItem("activeSnakeId");
            if (backgroundWorker) {
                backgroundWorker.postMessage({ type: 'clearSnake' });
            }
            const arenaUrl = window.ARENA_SERVICE_URL || 'http://localhost:8082';
            if (navigator.sendBeacon) {
                navigator.sendBeacon(`${arenaUrl}/api/v1/arena/snakes/${current}/leave`);
            }
            fetch(`${arenaUrl}/api/v1/arena/snakes/${current}`, { method: "DELETE", keepalive: true }).catch(() => {});
        }
    }

    // Keyboard controls
    window.addEventListener("keydown", (e) => {
        if (["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", " ", "e", "E", "f", "F"].includes(e.key)) {
            if (document.activeElement && (document.activeElement.tagName === "INPUT" || document.activeElement.tagName === "TEXTAREA")) {
                return;
            }
            e.preventDefault();
        }

        switch (e.key) {
            case "ArrowUp":
            case "w":
            case "W":
                changeDir(0, -1);
                break;
            case "ArrowDown":
            case "s":
            case "S":
                changeDir(0, 1);
                break;
            case "ArrowLeft":
            case "a":
            case "A":
                changeDir(-1, 0);
                break;
            case "ArrowRight":
            case "d":
            case "D":
                changeDir(1, 0);
                break;
            case " ":
            case "e":
            case "E":
            case "f":
            case "F":
                triggerSkill();
                break;
        }
    });

    window.addEventListener("beforeunload", cleanupCurrentSnake);

    // DOM Ready initialization
    document.addEventListener("DOMContentLoaded", () => {
        const elInpSidebar = document.getElementById("inputPlayerName");
        const elInpOverlay = document.getElementById("overlayPlayerName");
        if (elInpSidebar && elInpOverlay) {
            // Guarantee empty inputs on initial load so placeholder instructions are visible
            elInpSidebar.value = "";
            elInpOverlay.value = "";

            elInpSidebar.addEventListener("input", (e) => {
                elInpOverlay.value = e.target.value;
                updateTagBadges(e.target.value);
                if (window.lookupPlayerByName) window.lookupPlayerByName(e.target.value);
            });
            elInpOverlay.addEventListener("input", (e) => {
                elInpSidebar.value = e.target.value;
                updateTagBadges(e.target.value);
                if (window.lookupPlayerByName) window.lookupPlayerByName(e.target.value);
            });
            elInpOverlay.addEventListener("keydown", (e) => {
                if (e.key === "Enter") validateAndStartGame();
            });
            elInpSidebar.addEventListener("keydown", (e) => {
                if (e.key === "Enter") validateAndStartGame();
            });
        }

        if (window.updatePlayerIdBadge) window.updatePlayerIdBadge(null);
        updateTagBadges("");

        if (window.logApi) {
            window.logApi("INFO", "/system/init", 200, null, {
                status: "TACTICAL_CONSOLE_READY",
                pilot: window.registeredPlayerId || playerId,
                audioEngine: "WebAudioSynth",
                architecture: "Spring Boot Microservices Modular"
            });
        }

        if (window.renderLoop) requestAnimationFrame(window.renderLoop);
        if (window.fetchArenaState) window.fetchArenaState();
    });

    // Also run immediately in case DOMContentLoaded already fired
    if (document.readyState === "complete" || document.readyState === "interactive") {
        const elInpSidebar = document.getElementById("inputPlayerName");
        const elInpOverlay = document.getElementById("overlayPlayerName");
        if (elInpSidebar && elInpOverlay) {
            elInpSidebar.value = "";
            elInpOverlay.value = "";
        }
        updateTagBadges("");
        if (window.updatePlayerIdBadge) window.updatePlayerIdBadge(null);
        if (window.renderLoop) requestAnimationFrame(window.renderLoop);
        if (window.fetchArenaState) window.fetchArenaState();
    }

    // Export to SnakeArena namespace
    window.SnakeArena.App = {
        get playerId() { return playerId; },
        set playerId(v) { playerId = v; window.playerId = v; },
        get playerName() { return playerName; },
        set playerName(v) { playerName = v; window.playerName = v; },
        get selectedClass() { return selectedClass; },
        set selectedClass(v) { selectedClass = v; window.selectedClass = v; },
        get selectedColor() { return selectedColor; },
        set selectedColor(v) { selectedColor = v; window.selectedColor = v; },
        get snakeId() { return snakeId; },
        set snakeId(v) { snakeId = v; window.snakeId = v; },
        get score() { return score; },
        set score(v) { score = v; window.score = v; },
        get gameRunning() { return gameRunning; },
        set gameRunning(v) { gameRunning = v; window.gameRunning = v; },
        get skillActive() { return skillActive; },
        set skillActive(v) { skillActive = v; window.skillActive = v; },
        setPlayerIdentity,
        selectOverlayClass,
        selectSidebarClass,
        validateAndStartGame,
        deployCurrentSnake,
        lockInGameControls,
        endGame,
        changeDir,
        triggerSkill,
        updateSkillStatusUi,
        setSnakeId,
        renderLeaderboard,
        cleanupCurrentSnake,
        updateTagBadges,
        getDeviceProfile,
        saveDeviceProfile
    };

    // Backward-compatible global aliases
    window.playerId = playerId;
    window.playerName = playerName;
    window.selectedClass = selectedClass;
    window.selectedColor = selectedColor;
    window.snakeId = snakeId;
    window.score = score;
    window.gameRunning = gameRunning;
    window.skillActive = skillActive;
    window.backgroundWorker = backgroundWorker;
    window.setSnakeId = setSnakeId;
    window.selectOverlayClass = selectOverlayClass;
    window.selectSidebarClass = selectSidebarClass;
    window.validateAndStartGame = validateAndStartGame;
    window.deployCurrentSnake = deployCurrentSnake;
    window.lockInGameControls = lockInGameControls;
    window.endGame = endGame;
    window.changeDir = changeDir;
    window.triggerSkill = triggerSkill;
    window.updateSkillStatusUi = updateSkillStatusUi;
    window.renderLeaderboard = renderLeaderboard;
    window.cleanupCurrentSnake = cleanupCurrentSnake;
    window.updateTagBadges = updateTagBadges;
    window.getDeviceProfile = getDeviceProfile;
    window.saveDeviceProfile = saveDeviceProfile;

    // Optional query parameter for automated testing / live preview (?autostart=1)
    if (new URLSearchParams(window.location.search).get("autostart") === "1") {
        window.addEventListener("DOMContentLoaded", () => {
            setTimeout(() => {
                const inp = document.getElementById("overlayPlayerName");
                if (inp) {
                    inp.value = "Marvel";
                    updateTagBadges("Marvel");
                }
                validateAndStartGame();
            }, 600);
        });
    }
})();
