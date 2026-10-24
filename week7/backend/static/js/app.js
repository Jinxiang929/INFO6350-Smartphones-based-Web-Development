"use strict";

const $ = (id) => document.getElementById(id);

const ui = {
    app: $("speech-app"),
    auth: $("auth-status"),
    logout: $("logout-button"),

    start: $("start-button"),
    stop: $("stop-button"),
    transcript: $("transcript"),
    sttStatus: $("speech-status"),
    saveTranscript: $("save-button"),
    saveTranscriptStatus: $("save-status"),

    source: $("translation-source"),
    useTranscript: $("translation-use-transcript-button"),
    target: $("target-language"),
    translate: $("translate-button"),
    output: $("translation-output"),
    translationStatus: $("translation-status"),
    saveTranslation: $("save-translation-button"),
    translationSaveStatus: $("translation-save-status"),

    generate: $("generate-speech-button"),
    ttsStatus: $("tts-status"),
    audio: $("tts-audio")
};

const googleClientId = ui.app.dataset.googleClientId;

// Keep the application JWT in memory.
let accessToken = "";
let currentUserId = "";

let busy = false;
let recording = false;

let recorder = null;
let microphoneStream = null;
let recordingTimer = null;
let audioUrl = "";

let transcriptRecord = null;
let translationRecord = null;


function locked() {
    return !accessToken || busy || recording;
}


function validSpeechText(text) {
    return text.trim().length > 0 && text.length <= 2000;
}


function makeRecord(payload) {
    return {
        payload: Object.freeze({
            ...payload,
            record_id: crypto.randomUUID()
        }),
        saved: false
    };
}


function updateControls() {
    const disabled = locked();

    const translatedText =
        translationRecord?.payload.translated_text || "";

    ui.start.disabled = disabled;
    ui.stop.disabled = !recording || busy;
    ui.logout.disabled = !currentUserId || busy || recording;

    ui.source.disabled = disabled;
    ui.target.disabled = disabled;

    ui.useTranscript.disabled =
        disabled ||
        !validSpeechText(ui.transcript.value.trim());

    ui.translate.disabled =
        disabled || !validSpeechText(ui.source.value);

    ui.generate.disabled =
        disabled || !validSpeechText(translatedText);

    ui.saveTranscript.disabled =
        disabled ||
        !transcriptRecord ||
        transcriptRecord.saved;

    ui.saveTranslation.disabled =
        disabled ||
        !translationRecord ||
        translationRecord.saved;
}


function clearAudio() {
    ui.audio.pause();
    ui.audio.removeAttribute("src");
    ui.audio.load();
    ui.audio.hidden = true;

    if (audioUrl) {
        URL.revokeObjectURL(audioUrl);
        audioUrl = "";
    }
}


function clearTranslation() {
    translationRecord = null;

    ui.output.value = "";
    ui.translationStatus.textContent = "";
    ui.translationSaveStatus.textContent = "";

    clearAudio();
    ui.ttsStatus.textContent = "";
}


function clearFeatures() {
    transcriptRecord = null;

    ui.transcript.value = "";
    ui.saveTranscriptStatus.textContent = "";
    ui.sttStatus.textContent = "Sign in to start recording.";

    ui.source.value = "";
    ui.target.value = "es";

    clearTranslation();
}

function renderAccount(user = null) {
    const signedIn = Boolean(user);

    $("signed-out-panel").hidden = signedIn;
    $("signed-in-panel").hidden = !signedIn;

    $("profile-name").textContent = signedIn
        ? (user.name || "Signed-in user")
        : "";

    const email = signedIn ? (user.email || "") : "";

    $("profile-email").textContent = email;
    $("profile-email").hidden = !email;
}

function clearSession() {
    accessToken = "";
    currentUserId = "";

    renderAccount();
    ui.auth.textContent = "";

    clearFeatures();
    updateControls();
}


async function readApiResponse(response) {
    const data = await response.json().catch(() => ({}));

    if (response.status === 401) {
        clearSession();

        ui.auth.textContent =
            "Your session is unavailable or expired. Please sign in again.";
    }

    if (!response.ok || data.success !== true) {
        throw new Error(
            data.error ||
            `Request failed with status ${response.status}.`
        );
    }

    return data;
}


async function postJson(path, payload) {
    const headers = {
        "Content-Type": "application/json"
    };

    if (accessToken) {
        headers.Authorization = `Bearer ${accessToken}`;
    }

    const response = await fetch(path, {
        method: "POST",
        headers,
        body: JSON.stringify(payload)
    });

    return readApiResponse(response);
}


async function runTask(statusElement, work) {
    if (locked()) {
        return;
    }

    busy = true;
    updateControls();

    try {
        await work();
    } catch (error) {
        statusElement.textContent = `Failed: ${error.message}`;
    } finally {
        busy = false;
        updateControls();
    }
}


function loadGoogleSignIn() {
    if (!googleClientId) {
        ui.auth.textContent =
            "Google Client ID is missing. Check the server configuration.";
        return;
    }

    const script = document.createElement("script");

    script.src = "https://accounts.google.com/gsi/client?hl=en";
    script.async = true;

    script.addEventListener("load", () => {
        try {
            window.google.accounts.id.initialize({
                client_id: googleClientId,
                callback: handleGoogleCredential,
                ux_mode: "popup",
                auto_select: false
            });

            const container = $("google-button");

            // Remove any previously rendered button.
            container.replaceChildren();

            // Medium buttons do not display personalized account details.
            window.google.accounts.id.renderButton(
                $("google-button"),
                {
                    type: "standard",
                    theme: window.matchMedia(
                        "(prefers-color-scheme: dark)"
                    ).matches
                        ? "filled_black"
                        : "outline",
                    size: "large",
                    text: "signin_with",
                    shape: "pill",
                    width: 190,
                    logo_alignment: "left",
                    locale: "en"
                }
            );

            ui.auth.textContent = "Please sign in with Google.";
        } catch (error) {
            ui.auth.textContent =
                "Unable to initialize Google Sign-In.";
        }
    });

    script.addEventListener("error", () => {
        ui.auth.textContent =
            "Unable to load Google Sign-In. Check your connection.";
    });

    document.head.appendChild(script);
}


async function handleGoogleCredential(googleResponse) {
    if (busy || recording) {
        return;
    }

    if (!googleResponse.credential) {
        ui.auth.textContent = "No Google credential received.";
        return;
    }

    busy = true;
    clearSession();

    ui.auth.textContent = "Signing in...";

    try {
        const data = await postJson("/api/auth/google", {
            id_token: googleResponse.credential
        });

        if (!data.access_token || !data.user?.google_id) {
            throw new Error(
                "The backend did not return an application token."
            );
        }

        accessToken = data.access_token;
        currentUserId = data.user.google_id;

        // Display the profile returned by the verified backend login.
        renderAccount(data.user);

        ui.auth.textContent = "";
        ui.sttStatus.textContent = "Ready to record.";
    } catch (error) {
        ui.auth.textContent =
            `Sign-in failed: ${error.message}`;
    } finally {
        busy = false;
        updateControls();
    }
}


async function logout() {
    if (busy || recording || !currentUserId) {
        return;
    }

    const tokenToRevoke = accessToken;

    busy = true;
    clearSession();

    ui.auth.textContent = "Signing out...";

    try {
        window.google?.accounts?.id?.disableAutoSelect();

        const response = await fetch("/api/auth/logout", {
            method: "POST",
            headers: {
                Authorization: `Bearer ${tokenToRevoke}`
            }
        });

        if (!response.ok && response.status !== 401) {
            throw new Error("Server sign-out failed.");
        }

        ui.auth.textContent =
            "Signed out. Please sign in to continue.";
    } catch (error) {
        ui.auth.textContent =
            "Signed out locally. Server token revocation could not be confirmed.";
    } finally {
        busy = false;
        updateControls();
    }
}


function releaseMicrophone() {
    if (microphoneStream) {
        microphoneStream.getTracks().forEach((track) => {
            track.stop();
        });

        microphoneStream = null;
    }
}


async function startRecording() {
    if (locked()) {
        return;
    }

    if (
        !navigator.mediaDevices?.getUserMedia ||
        !window.MediaRecorder
    ) {
        ui.sttStatus.textContent =
            "Microphone recording is unavailable in this browser.";
        return;
    }

    const mimeType = [
        "audio/webm;codecs=opus",
        "audio/webm",
        "audio/mp4"
    ].find((type) => MediaRecorder.isTypeSupported(type));

    if (!mimeType) {
        ui.sttStatus.textContent =
            "No supported recording format was found. Please try Chrome.";
        return;
    }

    busy = true;

    ui.audio.pause();
    updateControls();

    ui.sttStatus.textContent =
        "Requesting microphone permission...";

    try {
        microphoneStream =
            await navigator.mediaDevices.getUserMedia({
                audio: true
            });

        const chunks = [];
        let failed = false;

        const activeRecorder = new MediaRecorder(
            microphoneStream,
            { mimeType }
        );

        recorder = activeRecorder;

        activeRecorder.ondataavailable = (event) => {
            if (event.data.size > 0) {
                chunks.push(event.data);
            }
        };

        activeRecorder.onerror = () => {
            failed = true;

            if (activeRecorder.state !== "inactive") {
                activeRecorder.stop();
            }
        };

        activeRecorder.onstop = () => {
            const blob = new Blob(chunks, {
                type: activeRecorder.mimeType
            });

            void transcribeRecording(
                blob,
                activeRecorder.mimeType,
                failed
            );
        };

        activeRecorder.start();
        recording = true;

        transcriptRecord = null;

        ui.transcript.value = "";
        ui.saveTranscriptStatus.textContent = "";

        ui.sttStatus.textContent =
            "Recording... Speak now, then click Stop Recording.";

        recordingTimer = setTimeout(stopRecording, 60000);
    } catch (error) {
        releaseMicrophone();

        ui.sttStatus.textContent =
            error.name === "NotAllowedError"
                ? "Microphone permission was denied."
                : "Unable to start recording. Check your microphone.";
    } finally {
        busy = false;
        updateControls();
    }
}


function stopRecording() {
    if (recorder?.state === "recording") {
        ui.stop.disabled = true;
        recorder.stop();
    }
}


async function transcribeRecording(blob, mimeType, failed) {
    clearTimeout(recordingTimer);
    releaseMicrophone();

    recorder = null;
    recording = false;
    busy = true;

    updateControls();

    try {
        if (failed || blob.size === 0) {
            throw new Error(
                "Recording failed or was empty. Please record again."
            );
        }

        ui.sttStatus.textContent =
            "Transcribing your recording...";

        const extension =
            mimeType.includes("mp4") ? "mp4" : "webm";

        const form = new FormData();

        form.append(
            "audio",
            blob,
            `recording.${extension}`
        );

        const response = await fetch(
            "/api/speech/transcribe",
            {
                method: "POST",
                headers: {
                    Authorization: `Bearer ${accessToken}`
                },
                body: form
            }
        );

        const data = await readApiResponse(response);

        if (
            typeof data.text !== "string" ||
            !data.text.trim()
        ) {
            throw new Error(
                "The server returned an empty transcript."
            );
        }

        const text = data.text.trim();

        // Transcription only displays the recognized text.
        ui.transcript.value = text;

        transcriptRecord = makeRecord({
            operation: "speech_to_text",
            text
        });

        ui.sttStatus.textContent =
            "Transcription complete. The transcript has not been saved.";

        ui.saveTranscriptStatus.textContent =
            "Click Save Transcript to save this text.";
    } catch (error) {
        ui.sttStatus.textContent =
            `Transcription failed: ${error.message}`;
    } finally {
        busy = false;
        updateControls();
    }
}


function useTranscriptForTranslation() {
    if (locked()) {
        return;
    }

    const text = ui.transcript.value.trim();

    if (!validSpeechText(text)) {
        return;
    }

    clearTranslation();
    ui.source.value = text;

    ui.translationStatus.textContent =
        "Transcript copied. Choose a target language and click Translate.";

    updateControls();
}


async function translateCore(text, targetLanguage) {
    ui.translationStatus.textContent = "Translating...";

    const data = await postJson("/api/translate", {
        text,
        source_language: "auto",
        target_language: targetLanguage
    });

    if (
        typeof data.translated_text !== "string" ||
        !data.translated_text.trim() ||
        data.source_text !== text ||
        data.target_language !== targetLanguage
    ) {
        throw new Error(
            "The server returned an invalid translation."
        );
    }

    const translatedText = data.translated_text.trim();

    ui.source.value = text;
    ui.target.value = targetLanguage;
    ui.output.value = translatedText;

    // Preserve one matching snapshot for manual saving.
    translationRecord = makeRecord({
        operation: "translation",
        text,
        translated_text: translatedText,
        source_language: "auto",
        target_language: targetLanguage
    });

    ui.translationStatus.textContent =
        translatedText.length <= 2000
            ? "Translation complete. Click Generate Speech to listen."
            : "Translation complete. Shorten the original text to generate audio within the 2000-character limit.";

    ui.translationSaveStatus.textContent =
        "Not saved. Click Save Translation to save the combined record.";
}


function translateFromForm() {
    const text = ui.source.value.trim();
    const targetLanguage = ui.target.value;

    if (!validSpeechText(text)) {
        return;
    }

    return runTask(ui.translationStatus, async () => {
        clearTranslation();
        await translateCore(text, targetLanguage);
    });
}


async function generateSpeechCore(text) {
    if (!validSpeechText(text)) {
        throw new Error(
            "Speech text must contain between 1 and 2000 characters."
        );
    }

    clearAudio();

    ui.ttsStatus.textContent = "Generating speech...";

    const response = await fetch("/api/speech/synthesize", {
        method: "POST",
        headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ text })
    });

    if (!response.ok) {
        await readApiResponse(response);
    }

    const contentType =
        response.headers.get("Content-Type") || "";

    if (!contentType.startsWith("audio/")) {
        throw new Error("The server did not return audio.");
    }

    const blob = await response.blob();

    if (blob.size === 0) {
        throw new Error("The generated audio is empty.");
    }

    audioUrl = URL.createObjectURL(blob);

    ui.audio.src = audioUrl;
    ui.audio.hidden = false;

    ui.ttsStatus.textContent =
        "Audio ready. Use the player to listen again.";

    try {
        await ui.audio.play();
    } catch (error) {
        ui.ttsStatus.textContent =
            "Audio ready. Press Play to listen.";
    }
}


function generateSpeech() {
    const text =
        translationRecord?.payload.translated_text || "";

    return runTask(ui.ttsStatus, async () => {
        await generateSpeechCore(text);
    });
}


function saveRecord(record, statusElement, label) {
    if (!record || record.saved) {
        return;
    }

    return runTask(statusElement, async () => {
        statusElement.textContent =
            "Saving to Google Sheets...";

        // Keep the same record ID when retrying a failed save.
        await postJson("/api/submit", record.payload);

        record.saved = true;

        statusElement.textContent =
            `${label} saved to Google Sheets.`;
    });
}


ui.start.addEventListener("click", startRecording);
ui.stop.addEventListener("click", stopRecording);
ui.logout.addEventListener("click", logout);

ui.useTranscript.addEventListener(
    "click",
    useTranscriptForTranslation
);

ui.translate.addEventListener(
    "click",
    translateFromForm
);

ui.generate.addEventListener(
    "click",
    generateSpeech
);

ui.saveTranscript.addEventListener("click", () => {
    void saveRecord(
        transcriptRecord,
        ui.saveTranscriptStatus,
        "Transcript"
    );
});

ui.saveTranslation.addEventListener("click", () => {
    void saveRecord(
        translationRecord,
        ui.translationSaveStatus,
        "Translation"
    );
});

ui.source.addEventListener("input", () => {
    clearTranslation();
    updateControls();
});

ui.target.addEventListener("change", () => {
    clearTranslation();
    updateControls();
});

ui.audio.addEventListener("play", () => {
    if (recording) {
        ui.audio.pause();
    }
});

window.addEventListener("pagehide", () => {
    clearTimeout(recordingTimer);

    if (recorder) {
        recorder.onstop = null;
        recorder.ondataavailable = null;

        if (recorder.state !== "inactive") {
            recorder.stop();
        }
    }

    releaseMicrophone();
    clearAudio();
});

updateControls();
loadGoogleSignIn();