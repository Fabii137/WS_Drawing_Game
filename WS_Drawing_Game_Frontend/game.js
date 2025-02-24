const username = sessionStorage.getItem("username");
if(!username || username === "") {
    window.location.href = 'index.html'
}

myTurn = false;

const socket = new WebSocket(`ws://localhost:8080?username=${username}`);

socket.onopen = () => {
    console.log("Connected to server");
}

const statusElement = document.getElementById("status");

socket.onmessage = (event) => {
    const data = JSON.parse(event.data);
    
    switch(data.type) {
        case "wait":
            statusElement.innerText = 'Status: Waiting for players';
            break;
    }
};
window.addEventListener('load', () => { 
	resize();
	document.addEventListener('mousedown', startPainting); 
	document.addEventListener('mouseup', stopPainting); 
	document.addEventListener('mousemove', sketch); 
	window.addEventListener('resize', resize); 


}); 
	
const canvas = document.querySelector('#canvas'); 

const ctx = canvas.getContext('2d'); 
	
function resize(){ 
    ctx.canvas.width = window.innerWidth / 2; 
    ctx.canvas.height = window.innerHeight / 1.7; 
} 
	
let coord = {x:0 , y:0}; 
let paint = false;
let color = 'black'; 

function getPosition(event){ 
    coord.x = event.clientX - canvas.offsetLeft; 
    coord.y = event.clientY - canvas.offsetTop; 
} 
function startPainting(event){ 
    if(myTurn) {
        paint = true; 
        getPosition(event); 
    }
} 
function stopPainting(){ 
    paint = false; 
} 

function setColor(setColor) {
    color = setColor;
}

function clearCanvas() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
}
	
function sketch(event){ 
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


