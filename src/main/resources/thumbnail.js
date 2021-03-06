window.addEventListener("load", function () {
    var resizeForm = document.getElementById("resize");
    var thumbnail = document.getElementById("thumbnail");
    var widthscale = document.getElementById("widthinput");
    var widthlabel = document.getElementById("widthlabel");
    var heightscale = document.getElementById("heightinput");
    var heightlabel = document.getElementById("heightlabel");

    widthscale.oninput = function(event) {
        widthlabel.textContent = widthscale.value
    };

    heightscale.oninput = function(event) {
        heightlabel.textContent = heightscale.value
    };

    // Upload the contents of the form and load the thumbnail
    function uploadForResize() {
        var XHR = new XMLHttpRequest();

        // We expect an image blob response
        XHR.responseType = "blob";

        // On completion, set the thumbnail image source
        XHR.onload = function(event) {
            thumbnail.src = window.URL.createObjectURL(XHR.response);
        };

        // On failure, popup with a warning
        XHR.onerror = function(event) {
            alert("Image upload failed");
        };

        // We setup our request
        XHR.open("POST", "/resize");

        // The data sent are the one the user provide in the form
        XHR.send(new FormData(resizeForm));
    }

    // Override form submission behaviour
    resizeForm.addEventListener("submit", function (event) {
        event.preventDefault();
        uploadForResize();
    });
});
