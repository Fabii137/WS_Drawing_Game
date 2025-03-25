const username = sessionStorage.getItem("username");
if(!username || username === "") {
    window.location.href = 'index.html'
    throw new Error("Username is null or empty, redirecting...");
}

const canvas = document.querySelector('#canvas'); 
const ctx = canvas.getContext('2d', { willReadFrequently: true });
// const socket = new WebSocket(`ws://147.93.126.146:3000?username=${username}`);
const socket = new WebSocket(`ws://localhost:3000?username=${username}`);
let mouseDown = false;
let resizeTimeout;
window.addEventListener('load', () => { 
	resize();
    window.addEventListener('mousedown', () => mouseDown = true);
    window.addEventListener('mouseup', () => mouseDown = false);
	window.addEventListener('resize', resize); 
}); 
canvas.addEventListener("mouseenter", () => {
    // if (mouseDown && myTurn) {
    //     isDrawing = true;
    //     setCoordPos(this);
    // }
});
canvas.addEventListener("mouseleave", () => {
    const mousePos = getMousePos(this);
    //if(myTurn)
        //TODO: send stroke till end of canvas so players dont miss it.
    isDrawing = false
});
canvas.addEventListener('mousedown', startDrawing); 
canvas.addEventListener('mouseup', stopDrawing); 
canvas.addEventListener('mousemove', draw); 

document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
        socket.send(JSON.stringify({ type: "get_canvas" }));
    }
});

document.getElementById("messageInput").addEventListener("keypress", function(event) {
    if (event.key === "Enter") {
        sendMessage();
    }
});

let myTurn = false;
let currentTurn = null;
let id = null;
let word = null;
let points = 0;
let interval = null;
let coord = {x:0 , y:0}; 
let isDrawing = false;
let color = 'rgba(0, 0, 0, 1)'; 
let fill = false;
let timeLeft = null;
let guessWord = null;



socket.onopen = () => {
    console.log("Connected to server");
}
socket.onclose = (event) => {
    const statusElement = document.getElementById("status");
    const timeElement = document.getElementById("time");
    const scoreboardElement = document.getElementById("player_list")

    reset();
    statusElement.innerText = 'Server connection closed!';
    timeElement.innerText = "";
    scoreboardElement.innerText = "";

    forceClear();
};

socket.onmessage = (event) => {
    const data = JSON.parse(event.data);
    // console.log(data)
    const statusElement = document.getElementById("status");
    const timeElement = document.getElementById("time");
    const optionElement = document.getElementById("options");
    const scoreboardElement = document.getElementById("player_list");
    const roundElement = document.getElementById("round");
    
    switch(data.type) {
        case "wait":
            reset();
            statusElement.innerText = 'Waiting for players';
            statusElement.style.color = 'white';
            timeElement.innerText = '';
            scoreboardElement.innerText = '';
            break;
        case "start":
            reset();
            resetScoreboard();
            currentTurn = data.id;
            statusElement.innerText = '';
            roundElement.innerText = `Round ${data.round}/${data.maxRound}`;
            myTurn = data.id == id;
            if (myTurn) {
                optionElement.style.visibility = 'visible'
            } else {
                clearInterval(interval);
                isDrawing = false;
                optionElement.style.visibility = 'hidden'

                guessWord = "ˍ".repeat(data.length)
                statusElement.innerText = guessWord.split("").join(" ");
            }
            addMessage("round starts!");
            break;
        case "id":
            id = data.data;
            break;
        case "scoreboard":
            const playerListElement = document.getElementById("player_list");
            playerListElement.innerHTML = '';
            const scoreboardPlayer = data.data;
            scoreboardPlayer.forEach(playerData => {
                addPlayerToScoreboard(playerData.id, playerData.username, playerData.points);
            });
            
            markTurn(); 
            break;
        case "guessed_players":
            const guessedPlayers = data.data;
            guessedPlayers.forEach(playerData => {
                markGuessed(playerData.id);
            });
            break;
        case "hint":
            let guessArray = guessWord.split("");
            guessArray[data.position] = data.letter;
            guessWord = guessArray.join("");
            statusElement.innerText = guessWord.split("").join(" ");
            break;
        case "message":
            addMessage(data.data, data.username)
            break;
        case "time":
            timeLeft = data.data;
            timeElement.innerText = `${timeLeft} seconds left!`;
            break;
        case "correct":
            addMessage(data.username + ' guessed the word!');
            markGuessed(data.id)
            break;
        case "clear":
            forceClear();
            break;
        case "word":
            word = data.data;
            document.getElementById("word").innerText = `Your word is: ${word}`;
            break;
        case "stroke":
            const { x1, y1, x2, y2, color, width } = data.data;
            const absX1 = x1 * canvas.width;
            const absY1 = y1 * canvas.height;
            const absX2 = x2 * canvas.width;
            const absY2 = y2 * canvas.height;
        
            drawStroke(absX1, absY1, absX2, absY2, color, width);
            break;
    }
};

// function updateCanvas(imgData) {
//     const img = new Image();
//     img.onload = function () {
//         ctx.clearRect(0, 0, canvas.width, canvas.height);
//         ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
//     };
//     img.src = imgData;
// }

function reset() {
    color = 'rgba(0, 0, 0, 1)';
    word = null;
    document.getElementById("word").innerText = "";
    myTurn = false;
    document.getElementById("round").innerText = "";
    guessWord = null;
}

function resize(){ 
    const rect = canvas.getBoundingClientRect();
    canvas.width = rect.width;
    canvas.height = rect.height;

    clearTimeout(resizeTimeout);

    resizeTimeout = setTimeout(() => {
        socket.send(JSON.stringify({ type: "get_strokes" }));
    }, 200);
    
} 

function getMousePos(event) {
    const rect = canvas.getBoundingClientRect();
    let pos = { x: event.clientX - rect.left, 
                y: event.clientY - rect.top };

    return pos;
}

function setCoordPos(event){ 
    const rect = canvas.getBoundingClientRect();
    coord.x = event.clientX - rect.left;
    coord.y = event.clientY - rect.top;
} 
function startDrawing(event){ 
    if(!myTurn)
        return;

    if(fill) {
        //TODO
        //bucketFill();
        setFill();
        return;
    }

    isDrawing = true; 
    setCoordPos(event); 
} 
function stopDrawing(){ 
    isDrawing = false; 
} 

function setColor(setColor) {
    color = setColor;
}
function setFill() {
    if(myTurn) {
        const fillBtn = document.getElementById("fill_btn");
        fill = !fill;
        fillBtn.style.backgroundColor = (fill) ? "lightblue" : "white";

        isDrawing = false;
    }
    
}


function forceClear() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
}

function clearCanvas() {
    if(myTurn) {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        // sendCanvasData();
        socket.send(JSON.stringify({ type: "clear"}));
    }
}
	
// function draw(event){
//     if (!isDrawing) 
//         return; 

//     ctx.beginPath(); 
//     ctx.lineWidth = 5; 
//     ctx.lineCap = 'round';     
//     ctx.strokeStyle = color; 

//     ctx.moveTo(coord.x, coord.y); 
//     setCoordPos(event); 
//     ctx.lineTo(coord.x , coord.y); 
//     ctx.stroke(); 
// }

function draw(event) {
    if (!isDrawing) return;

    let prevX = coord.x;
    let prevY = coord.y;
    setCoordPos(event);
    
    ctx.lineWidth = 5;
    ctx.lineCap = 'round';
    ctx.strokeStyle = color;

    ctx.beginPath();
    ctx.moveTo(prevX, prevY);
    ctx.lineTo(coord.x, coord.y);
    ctx.stroke();

    if (myTurn) {
        sendStroke(prevX, prevY, coord.x, coord.y, color, 5);
    }
}
function drawStroke(x1, y1, x2, y2, color, width) {
    ctx.strokeStyle = color;
    ctx.lineWidth = width;
    ctx.lineCap = "round";

    ctx.beginPath();
    ctx.moveTo(x1, y1);
    ctx.lineTo(x2, y2);
    ctx.stroke();
}

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

function sendMessage() {
    let input = document.getElementById("messageInput");
    let text = input.value.trim();
    if (text === "") 
        return;
    else if(text.length > 100) {
        let error = document.getElementById("error");
        error.innerText = "input cannot be longer than 100 characters!";

        setTimeout(() => {
            error.innerText = "";
        }, 3000)
        return;
    } else if(socket.readyState == WebSocket.CLOSED) {
        let error = document.getElementById("error");
        if(error.innerText == "") {
            error.innerText = "Not connected to server!";

            setTimeout(() => {
                error.innerText = "";
            }, 3000)
        }
        return;
    }

    addMessage(text, "");
    input.value = "";

    socket.send(JSON.stringify({ type: "message", data: text, username: username }));
    socket.send(JSON.stringify({ type: "get_canvas" }));
}

function addMessage(message, name) {
    let chat = document.getElementById("chat");
    
    let messageDiv = document.createElement("div");
    messageDiv.classList.add("message");

    let messageText = document.createElement("div");
    messageText.classList.add("text");
    messageText.textContent = message;

    let sender = document.createElement("span");
    if(name === "") {
        messageDiv.classList.add("myMessage");
        sender.textContent = "You"
    } else {
        sender.textContent = name;
    }
    

    messageDiv.appendChild(messageText);
    messageDiv.appendChild(sender);
    chat.appendChild(messageDiv);
    
    chat.scrollTop = chat.scrollHeight;
}

function addPlayerToScoreboard(playerID, name, points) {
    const playerListElement = document.getElementById("player_list");

    let playerDiv = document.getElementById(playerID);
    
    playerDiv = document.createElement("div");
    playerDiv.classList.add("player");
    playerDiv.id = playerID;

    let playerName = document.createElement("h5");
    playerName.textContent = name;
    let playerScore = document.createElement("span");
    playerScore.textContent = points;

    if(playerID == id) {
        playerDiv.style.border = "2px solid black"
    }

    playerDiv.appendChild(playerName);
    playerDiv.appendChild(playerScore);
    playerListElement.appendChild(playerDiv);
    
}


function resetScoreboard() {
    const playerListElement = document.getElementById("player_list");
    
    const players = playerListElement.getElementsByClassName("player");
    
    for (let player of players) {
        player.style.backgroundColor = "rgba(255, 255, 255, 0.3)"; 
        player.style.border = ""; 
    }
}

function markGuessed(playerID) {
    const player = document.getElementById(playerID);
    
    if(!player)
        return;

    player.style.background = "green";

}

function markTurn() {
    const player = document.getElementById(currentTurn);
    
    if(!player)
        return;

    player.style.backgroundColor = "orange"
}



function bucketFill(mousePos, targetColor, fillColor) {
   //TODO
}


