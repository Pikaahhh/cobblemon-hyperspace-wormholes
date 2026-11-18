// Recipe pictures: cycles the frames of a recipe with more than one way to make it (the donuts) every 1.5 seconds,
// and shows the game's tooltip for a slot next to the cursor, like in Minecraft.
function setUpRecipes() {
  document.querySelectorAll(".mc-anim").forEach(function (box) {
    if (box.dataset.cycling) return;
    box.dataset.cycling = "1";
    var frames = box.querySelectorAll(".mc-frame");
    var at = 0;
    frames[0].classList.add("is-on");
    setInterval(function () {
      frames[at].classList.remove("is-on");
      at = (at + 1) % frames.length;
      frames[at].classList.add("is-on");
    }, 1500);
  });
  var tip = document.querySelector(".mc-tip");
  if (!tip) {
    tip = document.createElement("img");
    tip.className = "mc-tip";
    tip.alt = "";
    tip.onload = function () {
      tip.style.width = tip.naturalWidth * (tip.dataset.scale || 2) + "px";
    };
    document.body.appendChild(tip);
  }
  document.querySelectorAll(".mc-slot[data-tip]").forEach(function (slot) {
    if (slot.dataset.ready) return;
    slot.dataset.ready = "1";
    var img = slot.parentElement.querySelector("img");
    slot.addEventListener("mouseenter", function () {
      // the tooltip is 2 screen pixels per font pixel when the recipe shows at full size (its width attribute, 3 per GUI
      // pixel; the picture itself is drawn at twice that for sharp screens), smaller with it
      var guiWidth = (Number(img.getAttribute("width")) || img.naturalWidth / 2) / 3;
      tip.dataset.scale = Math.max(1, (img.getBoundingClientRect().width / guiWidth) * 2 / 3);
      tip.src = new URL(slot.dataset.tip, img.src).href;
      tip.style.display = "block";
    });
    slot.addEventListener("mousemove", function (e) {
      tip.style.left = e.clientX + 12 + "px";
      tip.style.top = e.clientY - 18 + "px";
    });
    slot.addEventListener("mouseleave", function () {
      tip.style.display = "none";
    });
  });
}
if (typeof document$ !== "undefined") document$.subscribe(setUpRecipes);
else document.addEventListener("DOMContentLoaded", setUpRecipes);
