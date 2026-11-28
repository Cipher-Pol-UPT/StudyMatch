// a message's row lights up for a moment after going to it
export function flashMessage(element: HTMLElement) {
  element.classList.remove('sm-msg--flash');
  // read the layout so taking the class off and putting it back starts the animation again
  void element.offsetWidth;
  element.classList.add('sm-msg--flash');
  element.addEventListener('animationend', () => element.classList.remove('sm-msg--flash'), { once: true });
}
