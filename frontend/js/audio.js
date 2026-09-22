/**
 * Snake Arena: Clash of Serpents
 * Module: Web Audio API Micro-Synthesizer
 * --------------------------------------------------------------------------
 * Handles procedural sound synthesis without external audio files:
 * - click: UI mechanical feedback
 * - apple: Normal food collection
 * - golden: Rare core item chime arpeggio
 * - skill: Tactical speed/shield surge sweep
 * - death: Hull breach collision impact
 */

window.SnakeArena = window.SnakeArena || {};

(function() {
    let soundEnabled = true;
    let audioCtx = null;

    function getAudioContext() {
        if (!audioCtx) {
            const AudioContextClass = window.AudioContext || window.webkitAudioContext;
            if (AudioContextClass) {
                audioCtx = new AudioContextClass();
            }
        }
        if (audioCtx && audioCtx.state === 'suspended') {
            audioCtx.resume().catch(() => {});
        }
        return audioCtx;
    }

    function toggleSound() {
        soundEnabled = !soundEnabled;
        const textEl = document.getElementById("soundText");
        if (textEl) {
            textEl.innerText = soundEnabled ? "SFX: [ON]" : "SFX: [OFF]";
        }
        if (soundEnabled) {
            playSfx('click');
        }
    }

    function playSfx(type) {
        if (!soundEnabled) return;
        try {
            const ctx = getAudioContext();
            if (!ctx) return;
            const now = ctx.currentTime;

            if (type === 'click') {
                const osc = ctx.createOscillator();
                const gain = ctx.createGain();
                osc.type = 'sine';
                osc.frequency.setValueAtTime(600, now);
                osc.frequency.exponentialRampToValueAtTime(150, now + 0.04);
                gain.gain.setValueAtTime(0.12, now);
                gain.gain.linearRampToValueAtTime(0.01, now + 0.04);
                osc.connect(gain);
                gain.connect(ctx.destination);
                osc.start(now);
                osc.stop(now + 0.045);
            } else if (type === 'apple') {
                const osc = ctx.createOscillator();
                const gain = ctx.createGain();
                osc.type = 'triangle';
                osc.frequency.setValueAtTime(587, now);
                osc.frequency.setValueAtTime(880, now + 0.06);
                gain.gain.setValueAtTime(0.18, now);
                gain.gain.exponentialRampToValueAtTime(0.01, now + 0.14);
                osc.connect(gain);
                gain.connect(ctx.destination);
                osc.start(now);
                osc.stop(now + 0.15);
            } else if (type === 'golden') {
                const freqs = [523, 659, 1046];
                freqs.forEach((freq, idx) => {
                    const osc = ctx.createOscillator();
                    const gain = ctx.createGain();
                    osc.type = 'sine';
                    osc.frequency.setValueAtTime(freq, now + idx * 0.05);
                    gain.gain.setValueAtTime(0.2, now + idx * 0.05);
                    gain.gain.exponentialRampToValueAtTime(0.01, now + idx * 0.05 + 0.18);
                    osc.connect(gain);
                    gain.connect(ctx.destination);
                    osc.start(now + idx * 0.05);
                    osc.stop(now + idx * 0.05 + 0.19);
                });
            } else if (type === 'skill') {
                const osc = ctx.createOscillator();
                const gain = ctx.createGain();
                osc.type = 'sawtooth';
                osc.frequency.setValueAtTime(260, now);
                osc.frequency.exponentialRampToValueAtTime(950, now + 0.18);
                gain.gain.setValueAtTime(0.15, now);
                gain.gain.linearRampToValueAtTime(0.01, now + 0.22);
                osc.connect(gain);
                gain.connect(ctx.destination);
                osc.start(now);
                osc.stop(now + 0.23);
            } else if (type === 'death') {
                const osc = ctx.createOscillator();
                const gain = ctx.createGain();
                osc.type = 'square';
                osc.frequency.setValueAtTime(140, now);
                osc.frequency.exponentialRampToValueAtTime(35, now + 0.25);
                gain.gain.setValueAtTime(0.22, now);
                gain.gain.linearRampToValueAtTime(0.01, now + 0.26);
                osc.connect(gain);
                gain.connect(ctx.destination);
                osc.start(now);
                osc.stop(now + 0.27);
            }
        } catch (e) {}
    }

    // Export to SnakeArena namespace
    window.SnakeArena.Audio = {
        getAudioContext,
        toggleSound,
        playSfx,
        get soundEnabled() { return soundEnabled; },
        set soundEnabled(v) { soundEnabled = v; }
    };

    // Backward-compatible global aliases
    window.getAudioContext = getAudioContext;
    window.toggleSound = toggleSound;
    window.playSfx = playSfx;
    window.soundEnabled = soundEnabled;
})();
