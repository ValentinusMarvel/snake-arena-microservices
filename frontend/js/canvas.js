/**
 * Snake Arena: Clash of Serpents
 * Module: 2D Tactical Canvas Rendering Engine
 * --------------------------------------------------------------------------
 * Renders high-frequency 60 FPS arena graphics:
 * - Technical coordinate grid & perimeter hazard stripes
 * - Collectibles: Ruby Gem Apples & Glowing Golden Core Diamonds with satellites
 * - Serpent bodies with tapered segmentation, directional armored heads, and luminous optic eyes
 * - Tactical skill forcefield / aura effects and floating HUD callsigns
 */

window.SnakeArena = window.SnakeArena || {};

(function() {
    const canvas = document.getElementById("gameCanvas");
    const ctx = canvas ? canvas.getContext("2d") : null;
    const gridSize = 30;
    const tileSize = canvas ? canvas.width / gridSize : 20;

    function render() {
        if (!ctx || !canvas) return;

        const now = Date.now();
        const currentSnakes = window.arenaSnakes || [];
        const currentApples = window.arenaApples || [];
        const mySnakeId = window.snakeId;
        const isGameRunning = window.gameRunning;
        const mySkillActive = window.skillActive;
        const myColor = window.selectedColor || "#00e575";

        const enclosureEl = document.getElementById("canvasEnclosure");
        if (enclosureEl) {
            if (isGameRunning && mySkillActive) {
                enclosureEl.style.boxShadow = "0 8px 30px rgba(5, 150, 105, 0.25)";
                enclosureEl.style.borderColor = myColor;
            } else {
                enclosureEl.style.boxShadow = "var(--shadow-md)";
                enclosureEl.style.borderColor = "var(--border-highlight)";
            }
        }

        // Clean Warm White Canvas
        ctx.fillStyle = "#ffffff";
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        // Subtle Technical Coordinate Grid
        ctx.strokeStyle = "rgba(15, 23, 42, 0.045)";
        ctx.lineWidth = 1;
        for (let i = 0; i <= gridSize; i++) {
            const p = i * tileSize;
            ctx.beginPath();
            ctx.moveTo(p, 0);
            ctx.lineTo(p, canvas.height);
            ctx.stroke();

            ctx.beginPath();
            ctx.moveTo(0, p);
            ctx.lineTo(canvas.width, p);
            ctx.stroke();
        }

        // Perimeter Warning Lines (Soft Coral)
        ctx.strokeStyle = "rgba(225, 29, 72, 0.35)";
        ctx.lineWidth = 2;
        ctx.strokeRect(1, 1, canvas.width - 2, canvas.height - 2);

        // 1. RENDER APPLES / COLLECTIBLES
        currentApples.forEach(apple => {
            const cx = apple.x * tileSize + tileSize / 2;
            const cy = apple.y * tileSize + tileSize / 2;

            if (apple.type === "GOLDEN") {
                // GOLDEN APPLE: Radiant Core + Rotating Micro-Diamonds
                const pulse = 1 + Math.sin(now / 180) * 0.08;
                const r = (tileSize / 2.3) * pulse;

                // Radiant Aura
                const aura = ctx.createRadialGradient(cx, cy, 0, cx, cy, r * 2.2);
                aura.addColorStop(0, "rgba(251, 191, 36, 0.45)");
                aura.addColorStop(0.5, "rgba(245, 158, 11, 0.18)");
                aura.addColorStop(1, "transparent");
                ctx.fillStyle = aura;
                ctx.beginPath();
                ctx.arc(cx, cy, r * 2.2, 0, Math.PI * 2);
                ctx.fill();

                // Gold Jewel Body
                const grad = ctx.createRadialGradient(cx - r * 0.3, cy - r * 0.3, r * 0.1, cx, cy, r);
                grad.addColorStop(0, "#fef08a");
                grad.addColorStop(0.4, "#fbbf24");
                grad.addColorStop(0.85, "#d97706");
                grad.addColorStop(1, "#78350f");

                ctx.fillStyle = grad;
                ctx.beginPath();
                ctx.arc(cx, cy, r, 0, Math.PI * 2);
                ctx.fill();

                // Center Geometric Gold Core Diamond
                ctx.save();
                ctx.translate(cx, cy);
                ctx.fillStyle = "#ffffff";
                ctx.beginPath();
                const dSize = r * 0.45;
                ctx.moveTo(0, -dSize);
                ctx.lineTo(dSize * 0.7, 0);
                ctx.lineTo(0, dSize);
                ctx.lineTo(-dSize * 0.7, 0);
                ctx.closePath();
                ctx.fill();

                // 4 Rotating Diamond Micro-Satellites
                const angle = now / 400;
                for (let i = 0; i < 4; i++) {
                    const a = angle + (i * Math.PI / 2);
                    const sx = Math.cos(a) * (r * 1.35);
                    const sy = Math.sin(a) * (r * 1.35);
                    ctx.fillStyle = "#fde047";
                    ctx.beginPath();
                    ctx.arc(sx, sy, 1.8, 0, Math.PI * 2);
                    ctx.fill();
                }
                ctx.restore();
            } else {
                // NORMAL APPLE: Polished Ruby Gem with Emerald Stem Leaf
                const r = tileSize / 2.6;

                // Subtle Ground Glow
                ctx.fillStyle = "rgba(239, 68, 68, 0.15)";
                ctx.beginPath();
                ctx.arc(cx, cy, r * 1.6, 0, Math.PI * 2);
                ctx.fill();

                // Ruby Gem Gradient
                const grad = ctx.createRadialGradient(cx - r * 0.3, cy - r * 0.3, r * 0.1, cx, cy, r);
                grad.addColorStop(0, "#fca5a5");
                grad.addColorStop(0.35, "#ef4444");
                grad.addColorStop(0.8, "#b91c1c");
                grad.addColorStop(1, "#450a0a");

                ctx.fillStyle = grad;
                ctx.beginPath();
                ctx.arc(cx, cy + 1, r, 0, Math.PI * 2);
                ctx.fill();

                // Emerald Stem Leaf
                ctx.fillStyle = "#10b981";
                ctx.beginPath();
                ctx.ellipse(cx + 2, cy - r + 1, 3, 1.5, Math.PI / 4, 0, Math.PI * 2);
                ctx.fill();
            }
        });

        // 2. RENDER SNAKES: Directional Head, Eyes, & Segmented Exoskeleton
        currentSnakes.forEach(s => {
            const segs = s.segments || s.body;
            if (!s.alive || !segs || segs.length === 0) return;

            const isMe = (s.id === mySnakeId);
            const isSkillOn = isMe ? mySkillActive : (s.skillActive || false);
            const sColor = s.color || "#00e575";

            const head = segs[0];
            const headCx = head.x * tileSize + tileSize / 2;
            const headCy = head.y * tileSize + tileSize / 2;
            const pulse = 1.0 + Math.sin(now / 110) * 0.15;

            // Determine Direction Vector
            let dir = s.direction || "UP";
            if (segs.length > 1) {
                const neck = segs[1];
                if (head.x > neck.x) dir = "RIGHT";
                else if (head.x < neck.x) dir = "LEFT";
                else if (head.y > neck.y) dir = "DOWN";
                else if (head.y < neck.y) dir = "UP";
            }

            // A. SKILL FORCEFIELD / AURA
            if (isSkillOn) {
                ctx.save();
                const shieldColor = s.snakeType === "PYTHON" ? "#0284c7" : 
                                   (s.snakeType === "COBRA" ? "#d97706" : "#059669");
                const shieldRgba = s.snakeType === "PYTHON" ? "rgba(2, 132, 199, 0.15)" : 
                                  (s.snakeType === "COBRA" ? "rgba(217, 119, 6, 0.15)" : "rgba(5, 150, 105, 0.15)");

                // Trail Forcefield
                segs.forEach(seg => {
                    ctx.fillStyle = shieldRgba;
                    ctx.beginPath();
                    ctx.arc(seg.x * tileSize + tileSize / 2, seg.y * tileSize + tileSize / 2, tileSize * 0.75 * pulse, 0, Math.PI * 2);
                    ctx.fill();
                });

                // Bubble Shield
                ctx.strokeStyle = shieldColor;
                ctx.lineWidth = 2;
                ctx.beginPath();
                ctx.arc(headCx, headCy, tileSize * 1.15 * pulse, 0, Math.PI * 2);
                ctx.stroke();

                // Rotating Tactical Indicator
                const rotAngle = (now / 260) % (Math.PI * 2);
                ctx.setLineDash([3, 3]);
                ctx.lineWidth = 1.0;
                ctx.beginPath();
                ctx.arc(headCx, headCy, tileSize * 1.35 * pulse, rotAngle, rotAngle + Math.PI * 2);
                ctx.stroke();
                ctx.setLineDash([]);
                ctx.restore();
            }

            // B. DRAW BODY SEGMENTS (Tapered from neck to tail)
            for (let i = segs.length - 1; i >= 1; i--) {
                const seg = segs[i];
                const scx = seg.x * tileSize + tileSize / 2;
                const scy = seg.y * tileSize + tileSize / 2;

                const taper = 0.55 + 0.33 * (1 - i / segs.length);
                const segRadius = (tileSize / 2) * taper;

                ctx.save();
                ctx.fillStyle = sColor;
                ctx.beginPath();
                ctx.arc(scx, scy, segRadius, 0, Math.PI * 2);
                ctx.fill();

                // Inner Dorsal Core Highlight
                ctx.fillStyle = "rgba(255, 255, 255, 0.3)";
                ctx.beginPath();
                ctx.arc(scx, scy, segRadius * 0.45, 0, Math.PI * 2);
                ctx.fill();
                ctx.restore();
            }

            // C. DRAW DIRECTION-AWARE HEAD & PLAYFUL EXPRESSIVE EYES
            ctx.save();
            ctx.translate(headCx, headCy);

            let headAngle = 0;
            if (dir === "DOWN") headAngle = Math.PI;
            else if (dir === "LEFT") headAngle = -Math.PI / 2;
            else if (dir === "RIGHT") headAngle = Math.PI / 2;
            ctx.rotate(headAngle);

            // Armored Head Shell (Pointing UP in local space)
            const hr = tileSize * 0.48;
            ctx.fillStyle = sColor;
            ctx.beginPath();
            ctx.moveTo(0, -hr * 1.15); // Snout tip
            ctx.lineTo(hr * 0.95, hr * 0.7);
            ctx.quadraticCurveTo(0, hr * 0.4, -hr * 0.95, hr * 0.7);
            ctx.closePath();
            ctx.fill();

            // Head crown highlight for local player
            if (isMe) {
                ctx.fillStyle = "rgba(255, 255, 255, 0.4)";
                ctx.beginPath();
                ctx.arc(0, -hr * 0.3, hr * 0.38, 0, Math.PI * 2);
                ctx.fill();
            }

            // Playful Expressive Eyes
            const eyeX = hr * 0.42;
            const eyeY = -hr * 0.25;
            const eyeR = hr * 0.26;

            [-1, 1].forEach(side => {
                const ex = side * eyeX;

                // Outer white sclera
                ctx.fillStyle = "#ffffff";
                ctx.beginPath();
                ctx.arc(ex, eyeY, eyeR, 0, Math.PI * 2);
                ctx.fill();
                ctx.strokeStyle = "rgba(15, 23, 42, 0.2)";
                ctx.lineWidth = 1;
                ctx.stroke();

                // Dark pupil
                ctx.fillStyle = "#0f172a";
                ctx.beginPath();
                ctx.arc(ex, eyeY - eyeR * 0.15, eyeR * 0.55, 0, Math.PI * 2);
                ctx.fill();

                // Cute catchlight shine
                ctx.fillStyle = "#ffffff";
                ctx.beginPath();
                ctx.arc(ex + 1, eyeY - eyeR * 0.3, eyeR * 0.22, 0, Math.PI * 2);
                ctx.fill();
            });

            ctx.restore();

            // D. FLOATING CALLSIGN & CLASS HUD BADGE
            ctx.save();
            ctx.textAlign = "center";

            if (isSkillOn) {
                const badgeText = s.snakeType === "PYTHON" ? "[PERISAI]" : 
                                 (s.snakeType === "COBRA" ? "[GOLD SENSE]" : "[AKSELERASI]");
                const badgeColor = s.snakeType === "PYTHON" ? "#0284c7" : 
                                  (s.snakeType === "COBRA" ? "#d97706" : "#059669");
                ctx.font = "bold 9px 'JetBrains Mono', monospace";
                const tw = ctx.measureText(badgeText).width;
                const by = head.y * tileSize - 22;

                ctx.fillStyle = "#ffffff";
                ctx.beginPath();
                ctx.roundRect(headCx - tw / 2 - 6, by, tw + 12, 14, 4);
                ctx.fill();
                ctx.strokeStyle = badgeColor;
                ctx.lineWidth = 1.5;
                ctx.stroke();

                ctx.fillStyle = badgeColor;
                ctx.fillText(badgeText, headCx, by + 10);
            }

            ctx.font = isMe ? "700 11px 'Plus Jakarta Sans', sans-serif" : "600 10px 'Plus Jakarta Sans', sans-serif";
            ctx.fillStyle = isMe ? "#059669" : "#334155";
            const label = isMe ? `${s.playerName} (ANDA)` : s.playerName;
            ctx.fillText(label, headCx, head.y * tileSize - (isSkillOn ? 25 : 6));
            ctx.restore();
        });
    }

    function renderLoop() {
        render();
        requestAnimationFrame(renderLoop);
    }

    // Immediately start loop
    requestAnimationFrame(renderLoop);

    window.SnakeArena.Canvas = {
        canvas,
        ctx,
        gridSize,
        tileSize,
        render,
        renderLoop
    };

    window.canvas = canvas;
    window.ctx = ctx;
    window.gridSize = gridSize;
    window.tileSize = tileSize;
    window.render = render;
    window.renderLoop = renderLoop;
})();
