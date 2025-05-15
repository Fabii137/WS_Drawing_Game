/* USER AUTHENTICATION */

// Retrieve the username from session storage and redirect to login if not found
const username = sessionStorage.getItem("username");
if (!username || username === "") {
    window.location.href = 'index.html';
    throw new Error("Username is null or empty, redirecting...");
}

/* CANVAS SETUP */

// Select the canvas element and get its 2D rendering context
const canvas = document.querySelector('#canvas'); 
const ctx = canvas.getContext('2d', { willReadFrequently: true });

// Resize time out for requesting strokes
let resizeTimeout;

// Event listeners for window and canvas interactions
window.addEventListener('load', () => { 
    resize(); // Adjust canvas size on load
    window.addEventListener('resize', resize); // Adjust canvas size on resize
}); 
canvas.addEventListener('mousedown', startDrawing); 
canvas.addEventListener('mouseup', stopDrawing); 
canvas.addEventListener('mousemove', draw); 

// Event listener for sending chat messages on Enter
document.getElementById("messageInput").addEventListener("keypress", function(event) {
    if (event.key === "Enter") {
        sendMessage();
    }
});

/* GAME STATE VARIABLES */

let myTurn = false;                 // Indicates if it's the player's turn
let currentTurn = null;             // ID of the current turn player
let id = null;                      // Player's ID
let word = null;                    // Word to be guessed or drawn
let points = 0;                     // Player's points
let coord = { x: 0, y: 0 };         // Current mouse coordinates while drawing
let isDrawing = false;              // Indicates if the player is drawing
let color = 'rgba(0, 0, 0, 1)';   // Current drawing color
let lineWidth = 5;                  // Current line width
let timeLeft = null;                // Time left in the round
let guessWord = null;               // Word being guessed by players, filled with underscores at start

/* HTML ELEMENTS */
const statusElement = document.getElementById("status");
const timeElement = document.getElementById("time");
const optionElement = document.getElementById("options");
const scoreboardElement = document.getElementById("player_list");
const roundElement = document.getElementById("round");
const wordElement = document.getElementById("word");

/* WEBSOCKET CONNECTION */

// Establish a WebSocket connection to the server
// const socket = new WebSocket(`ws://http://147.93.126.146:3000?username=${username}`);
const socket = new WebSocket(`ws://localhost:3000?username=${username}`);

// WebSocket connection opened
socket.onopen = () => {
    console.log("Connected to server");
};

// WebSocket connection closed
socket.onclose = (event) => {
    resetGameState();
    statusElement.innerText = 'Server connection closed!';
    timeElement.innerText = "";
    scoreboardElement.innerText = "";

    forceClear();
};

// Handle incoming WebSocket messages
socket.onmessage = (event) => {
    const data = JSON.parse(event.data);
    
    switch (data.type) {
        case "wait":
            resetGameState();
            statusElement.innerText = 'Waiting for players';
            statusElement.style.color = 'white';
            timeElement.innerText = '';
            scoreboardElement.innerText = '';
            break;
        case "start":
            resetGameState();
            resetScoreboard();
            currentTurn = data.id;
            statusElement.innerText = '';
            roundElement.innerText = `Round ${data.round}/${data.maxRound}`;
            myTurn = data.id == id;
            if (myTurn) {
                optionElement.style.visibility = 'visible'; // Show drawing options
            } else {
                isDrawing = false;
                optionElement.style.visibility = 'hidden'; // Hide drawing options

                // Display the word as underscores for guessing
                guessWord = "ˍ".repeat(data.length);
                statusElement.innerText = guessWord.split("").join(" ");
            }
            addMessage("round starts!");
            break;
        case "id":
            id = data.data; // Set player ID
            break;
        case "scoreboard":
            scoreboardElement.innerHTML = '';
            const scoreboardPlayer = data.data;
            scoreboardPlayer.forEach(playerData => {
                addPlayerToScoreboard(playerData.id, playerData.username, playerData.points);
            });
            markTurn(); // Highlight the current turn player
            break;
        case "guessed_players":
            const guessedPlayers = data.data;
            guessedPlayers.forEach(playerData => {
                markGuessed(playerData.id); // Mark players who guessed correctly
            });
            break;
        case "hint":
            let guessArray = guessWord.split("");
            guessArray[data.position] = data.letter; // Reveal a letter as a hint
            guessWord = guessArray.join("");
            statusElement.innerText = guessWord.split("").join(" ");
            break;
        case "message":
            addMessage(data.data, data.username); // Display chat message
            break;
        case "time":
            timeLeft = data.data;
            timeElement.innerText = `${timeLeft} seconds left!`; // Update timer
            break;
        case "correct":
            addMessage(data.username + ' guessed the word!');
            markGuessed(data.id); // Mark the player who guessed correctly
            break;
        case "clear":
            forceClear(); // Clear the canvas
            break;
        case "word":
            word = data.data; // Set the word for the current turn
            wordElement.innerText = `Your word is: ${word}`;
            break;
        case "stroke":
            const { x1, y1, x2, y2, color, width } = data.data;
            const absX1 = x1 * canvas.width;
            const absY1 = y1 * canvas.height;
            const absX2 = x2 * canvas.width;
            const absY2 = y2 * canvas.height;
        
            drawStroke(absX1, absY1, absX2, absY2, color, width); // Draw the stroke
            break;
    }
};

/* GAME STATE MANAGEMENT */

// Reset game state variables
function resetGameState() {
    color = 'rgba(0, 0, 0, 1)';
    word = null;
    wordElement.innerText = "";
    myTurn = false;
    roundElement.innerText = "";
    guessWord = null;
}

// Reset the scoreboard styles
function resetScoreboard() {
    const players = scoreboardElement.getElementsByClassName("player");
    
    for (let player of players) {
        player.style.backgroundColor = "rgba(255, 255, 255, 0.3)"; 
        player.style.border = ""; 
    }
}

// Mark a player as having guessed the word
function markGuessed(playerID) {
    const player = document.getElementById(playerID);
    if (!player) return;
    player.style.background = "green";
}

// Highlight the player whose turn it is
function markTurn() {
    const player = document.getElementById(currentTurn);
    if (!player) return;
    player.style.backgroundColor = "orange";
}

/* CANVAS DRAWING FUNCTIONS */

// Resize the canvas to fit the window
function resize() { 
    const rect = canvas.getBoundingClientRect();
    canvas.width = rect.width;
    canvas.height = rect.height;

    clearTimeout(resizeTimeout);

    if (socket.readyState == WebSocket.OPEN) {
        resizeTimeout = setTimeout(() => {
            socket.send(JSON.stringify({ type: "get_strokes" })); // Request strokes after resizing
        }, 200);
    }
}

// Clear the canvas 
function forceClear() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
}

// Clear the canvas and send to the server
function clearCanvas() {
    if (myTurn) {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        socket.send(JSON.stringify({ type: "clear" }));
    }
}

// Get mouse position relative to the canvas
function getMousePos(event) {
    const rect = canvas.getBoundingClientRect();
    return { x: event.clientX - rect.left, y: event.clientY - rect.top };
}

// Set the current mouse coordinates
function setCoordPos(event) { 
    const rect = canvas.getBoundingClientRect();
    coord.x = event.clientX - rect.left;
    coord.y = event.clientY - rect.top;
}

// Start drawing on the canvas
function startDrawing(event) { 
    if (!myTurn) return; // Only allow drawing if it's the player's turn

    isDrawing = true; 
    setCoordPos(event); 
}

// Stop drawing on the canvas
function stopDrawing() { 
    isDrawing = false; 
}

// Draw on the canvas while the mouse is moving
function draw(event) {
    if (!isDrawing) return;
    
    let prevX = coord.x;
    let prevY = coord.y;
    setCoordPos(event);
    
    ctx.lineWidth = lineWidth;
    ctx.lineCap = 'round';
    ctx.strokeStyle = color;

    ctx.beginPath();
    ctx.moveTo(prevX, prevY);
    ctx.lineTo(coord.x, coord.y);
    ctx.stroke();

    if (myTurn) {
        sendStroke(prevX, prevY, coord.x, coord.y, color, lineWidth); // Send stroke data to the server
    }
}

// Draw a stroke on the canvas
function drawStroke(x1, y1, x2, y2, color, width) {
    ctx.strokeStyle = color;
    ctx.lineWidth = width;
    ctx.lineCap = "round";

    ctx.beginPath();
    ctx.moveTo(x1, y1);
    ctx.lineTo(x2, y2);
    ctx.stroke();
}

// Send stroke data to the server
function sendStroke(x1, y1, x2, y2, color, width) {
    const normX1 = x1 / canvas.width;
    const normY1 = y1 / canvas.height;
    const normX2 = x2 / canvas.width;
    const normY2 = y2 / canvas.height;

    socket.send(JSON.stringify({
        type: "stroke",
        data: {
            x1: normX1,
            y1: normY1,
            x2: normX2,
            y2: normY2,
            color: color,
            width: width
        },
    }));
}

// Set the drawing color
function setColor(setColor) {
    color = setColor;
    const colorElement = document.getElementById("current-color");
    if (colorElement) colorElement.style.backgroundColor = color;
}

// Set the line width for drawing
function setLineWidth(lineW) {
    lineWidth = lineW;
}

/* CHAT FUNCTIONALITY */

// Send a chat message to the server
function sendMessage() {
    let input = document.getElementById("messageInput");
    let text = input.value.trim();
    if (text === "") return;
    else if (socket.readyState != WebSocket.OPEN) {
        showError("Not connected to server!");
        return;
    } else if (text.length > 100) {
        showError("Input cannot be longer than 100 characters!");
        return;
    } 

    addMessage(text, ""); // Add the message to the chat
    input.value = "";

    socket.send(JSON.stringify({ type: "message", data: text, username: username }));
}

// Add a message to the chat
function addMessage(message, name) {
    let chat = document.getElementById("chat");
    
    let messageDiv = document.createElement("div");
    messageDiv.classList.add("message");

    let messageText = document.createElement("div");
    messageText.classList.add("text");
    messageText.textContent = message;

    let sender = document.createElement("span");
    if (name === "") {
        messageDiv.classList.add("myMessage");
        sender.textContent = "You";
    } else {
        sender.textContent = name;
    }
    
    messageDiv.appendChild(messageText);
    messageDiv.appendChild(sender);
    chat.appendChild(messageDiv);
    
    chat.scrollTop = chat.scrollHeight; // Scroll to the bottom of the chat
}

/* SCOREBOARD MANAGEMENT */

// Add a player to the scoreboard
function addPlayerToScoreboard(playerID, name, points) {
    let playerDiv = document.getElementById(playerID);
    
    playerDiv = document.createElement("div");
    playerDiv.classList.add("player");
    playerDiv.id = playerID;

    let playerName = document.createElement("h5");
    playerName.textContent = name;
    let playerScore = document.createElement("span");
    playerScore.textContent = points;

    if (playerID == id) {
        playerDiv.style.border = "2px solid black"; // Highlight the current player
    }

    playerDiv.appendChild(playerName);
    playerDiv.appendChild(playerScore);
    scoreboardElement.appendChild(playerDiv);
}

/* ERROR HANDLING */

// Display an error message
function showError(message) {
    const error = document.getElementById("error");
    error.innerText = message;
    setTimeout(() => {
        error.innerText = "";
    }, 3000);
}