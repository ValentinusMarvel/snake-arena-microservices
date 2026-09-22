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
    let selectedColor = "#00e575";
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
        sessionStorage.setItem(tabInstanceKey, id);
        window.playerId = id;
        window.playerName = name;
    }

    function selectOverlayClass(className, color, el) {
        if (gameRunning) return;
        if (window.playSfx) window.playSfx('click');
        selectedClass = className;
        selectedColor = color;
        window.selectedClass = className;
        window.selectedColor = color;

        document.querySelectorAll('.overlay-class-opt').forEach(opt => opt.classList.remove('active'));
        if (el) el.classList.add('active');

        document.querySelectorAll('#sidebarClassSelector .class-card').forEach(c => {
            c.classList.toggle('selected', c.dataset.class === className);
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
            c.classList.toggle('selected', c.dataset.class === className);
        });

        document.querySelectorAll('.overlay-class-opt').forEach(opt => {
            opt.classList.toggle('active', opt.dataset.class === className);
        });

        const hudC = document.getElementById("hudClass");
        if (hudC) {
            hudC.innerText = className;
            hudC.style.color = color;
        }
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

        const startBtn = document.querySelector(".btn-deploy");
        const originalText = startBtn ? startBtn.innerText : "MASUK KE ARENA";
        if (startBtn) {
            startBtn.innerText = "CONNECTING LINK...";
            startBtn.disabled = true;
        }

        try {
            const playerRes = await fetch(`${window.PLAYER_SERVICE_URL || 'http://localhost:8081'}/api/v1/players`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ username: val, snakeType: selectedClass })
            });

            if (playerRes.ok) {
                const playerData = await playerRes.json();
                setPlayerIdentity(playerData.id, playerData.username);
                if (window.updatePlayerIdBadge) window.updatePlayerIdBadge(playerData.id);
            } else if (playerRes.status === 400) {
                const queryRes = await fetch(`${window.PLAYER_SERVICE_URL || 'http://localhost:8081'}/api/v1/players`);
                if (queryRes.ok) {
                    const list = await queryRes.json();
                    if (Array.isArray(list)) {
                        const existing = list.find(p => p.username && p.username.toLowerCase() === val.toLowerCase());
                        if (existing && existing.id) {
                            setPlayerIdentity(existing.id, existing.username);
                            if (window.updatePlayerIdBadge) window.updatePlayerIdBadge(existing.id);
                        }
                    }
                }
            }
        } catch (err) {}

        const finalName = playerName || val;
        await deployCurrentSnake(finalName, selectedClass, selectedColor);

        if (startBtn) {
            startBtn.innerText = originalText;
            startBtn.disabled = false;
        }
    }

    async function deployCurrentSnake(callsign, sClass, sColor) {
        try {
            const joinPayload = {
                playerId: window.registeredPlayerId || playerId,
                playerName: callsign,
                snakeType: sClass,
                color: sColor
            };

            const arenaRes = await fetch(`${window.ARENA_SERVICE_URL || 'http://localhost:8082'}/api/v1/arena/snakes`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(joinPayload)
            });

            if (!arenaRes.ok) {
                const errData = await arenaRes.json().catch(() => ({}));
                alert("Gagal deploy serpent: " + (errData.error || arenaRes.statusText));
                return;
            }

            const snakeData = await arenaRes.json();
            snakeId = snakeData.id;
            window.snakeId = snakeId;
            sessionStorage.setItem("activeSnakeId", snakeId);

            backgroundWorker.postMessage({
                type: 'setSnake',
                snakeId: snakeId,
                arenaUrl: window.ARENA_SERVICE_URL || 'http://localhost:8082'
            });

            score = 0;
            window.score = 0;
            gameRunning = true;
            window.gameRunning = true;

            const overlay = document.getElementById("gameOverlay");
            if (overlay) overlay.classList.add("hidden");

            const hudP = document.getElementById("hudPlayer");
            if (hudP) {
                hudP.innerText = callsign;
                hudP.style.color = "#ffffff";
            }
            const hudC = document.getElementById("hudClass");
            if (hudC) {
                hudC.innerText = sClass;
                hudC.style.color = sColor;
            }
            const hudS = document.getElementById("hudScore");
            if (hudS) hudS.innerText = "0";

            lockInGameControls(true);

            if (window.logApi) {
                window.logApi("POST", "/arena/snakes (Deploy)", 200, joinPayload, {
                    status: "SERPENT_SPAWNED",
                    snakeId: snakeId,
                    tacticalClass: sClass,
                    assignedCoordinates: `[${snakeData.x}, ${snakeData.y}]`
                });
            }

            if (window.fetchArenaState) await window.fetchArenaState();
        } catch (e) {
            alert("Kesalahan koneksi ke Arena Service: " + e.message);
        }
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
        if (!snakeId || !gameRunning) return;
        const now = Date.now();

        if (skillActive) return;

        if (now < skillCooldownUntil) {
            const sec = Math.ceil((skillCooldownUntil - now) / 1000);
            updateSkillStatusUi(`[RECHARGE: ${sec}s]`, "var(--solar-amber)");
            return;
        }

        if (window.playSfx) window.playSfx('skill');
        skillActive = true;
        window.skillActive = true;
        skillActiveUntil = now + 3000;
        skillCooldownUntil = now + 3000 + 5000;
        updateSkillStatusUi("[ACTIVE: 3s]", "var(--tactical-emerald)");

        const url = `${window.ARENA_SERVICE_URL || 'http://localhost:8082'}/api/v1/arena/snakes/${snakeId}/skill?active=true`;
        if (window.makeApiCall) await window.makeApiCall("PATCH", url);

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
                    if (window.makeApiCall) {
                        window.makeApiCall("PATCH", `${window.ARENA_SERVICE_URL || 'http://localhost:8082'}/api/v1/arena/snakes/${snakeId}/skill?active=false`, null, true);
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
        const list = document.getElementById("leaderboardList");
        if (!list) return;

        if (!leaderboard || leaderboard.length === 0) {
            list.innerHTML = `<div style="color:var(--text-dim); font-size:0.7rem; text-align:center; padding:10px;">Belum ada serpent aktif</div>`;
            return;
        }

        list.innerHTML = leaderboard.slice(0, 5).map((s, idx) => {
            const isUser = (s.id === snakeId);
            return `
                <div class="lb-item ${isUser ? 'user-snake' : ''}">
                    <span style="display:flex; align-items:center; gap:8px;">
                        <b style="color:var(--solar-amber);">#${idx + 1}</b>
                        <span style="color:${s.color || '#fff'}; font-weight:700;">${s.playerName}${isUser ? ' (YOU)' : ''}</span>
                    </span>
                    <span style="font-weight:700; color:#cbd5e1;">${s.score} pts</span>
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
        if (["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", " "].includes(e.key)) {
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
                if (window.lookupPlayerByName) window.lookupPlayerByName(e.target.value);
            });
            elInpOverlay.addEventListener("input", (e) => {
                elInpSidebar.value = e.target.value;
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
        renderLeaderboard,
        cleanupCurrentSnake
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
})();
