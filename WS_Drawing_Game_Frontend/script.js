document.getElementById("input").addEventListener("keypress", function(event) {
    if (event.key === "Enter") {
        submitLogin();
    }
});


function submitLogin() {
    const input = document.getElementById("input").value;
    if(input === "")
        return;

    sessionStorage.setItem("username", input);
    window.location.href = 'game.html'
}