const username = sessionStorage.getItem("username");
if(!username || username === "") {
    window.location.href = 'index.html'
}

window.addEventListener('load', () => { 
	resize();
	document.addEventListener('mousedown', startDrawing); 
	document.addEventListener('mouseup', stopDrawing); 
	document.addEventListener('mousemove', draw); 
	window.addEventListener('resize', resize); 

    // socket.send(JSON.stringify({ type: "get_canvas" }));
}); 

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

myTurn = false;
word = null;
points = 0;

const socket = new WebSocket(`ws://localhost:8080?username=${username}`);

socket.onopen = () => {
    console.log("Connected to server");
}

socket.onmessage = (event) => {
    const data = JSON.parse(event.data);
    console.log(data)
    const statusElement = document.getElementById("status");
    const pointsElement = document.getElementById("points");
    
    switch(data.type) {
        case "wait":
            reset();
            statusElement.innerText = 'Status: Waiting for players';
            break;
        case "start":
            reset();
            myTurn = data.turn == username;
            statusElement.innerText = (myTurn) ? 'Status: Your Turn!' : 'Status: Current Turn: ' + data.turn;
            addMessage("round starts!");
            break;
        case "message":
            addMessage(data.data, data.username)
            break;
        case "points":
            points = data.data;
            pointsElement.innerText = `Score: ${points}`;
            break;
        case "correct":
            addMessage(data.username + ' guessed the word!');
        case "clear":
            forceClear();
            break;
        case "word":
            word = data.data;
            document.getElementById("word").innerText = `Your word is ${word}`;
            break;
        case "canvas":
            updateCanvas(data.data);
            break;
    }
};

const canvas = document.querySelector('#canvas'); 
const ctx = canvas.getContext('2d'); 

function sendCanvasData() {
    const imgData = canvas.toDataURL("image/png"); 
    socket.send(JSON.stringify({ type: "canvas", data: imgData }));
}

function updateCanvas(imgData) {
    const img = new Image();
    img.onload = function () {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
    };
    img.src = imgData;
}

function reset() {
    color = 'black';
    word = null;
    document.getElementById("word").innerText = "";
    myTurn = false;
}

function resize(){ 
    ctx.canvas.width = window.innerWidth / 2; 
    ctx.canvas.height = window.innerHeight / 1.7; 

    if(socket.readyState == WebSocket.OPEN) {
        setTimeout(() => {
            socket.send(JSON.stringify({ type: "get_canvas" }));
        }, 100);
    }
    
} 
	
let coord = {x:0 , y:0}; 
let paint = false;
let color = 'black'; 

function getPosition(event){ 
    coord.x = event.clientX - canvas.offsetLeft; 
    coord.y = event.clientY - canvas.offsetTop; 
} 
function startDrawing(event){ 
    if(myTurn) {
        paint = true; 
        getPosition(event); 
    }
} 
function stopDrawing(){ 
    paint = false; 

    if(myTurn) {
        sendCanvasData();
    }
} 

function setColor(setColor) {
    color = setColor;
}

function forceClear() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
}

function clearCanvas() {
    if(myTurn) {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        sendCanvasData();
    }
}
	
function draw(event){ 
    if (!paint) 
        return; 

    ctx.beginPath(); 
    ctx.lineWidth = 5; 
    ctx.lineCap = 'round';     
    ctx.strokeStyle = color; 
    ctx.moveTo(coord.x, coord.y); 
    getPosition(event); 
    ctx.lineTo(coord.x , coord.y); 
    ctx.stroke(); 
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
    }

    addMessage(text, "You");
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
    sender.textContent = name;

    messageDiv.appendChild(messageText);
    messageDiv.appendChild(sender);
    chat.appendChild(messageDiv);
    
    chat.scrollTop = chat.scrollHeight;
}


