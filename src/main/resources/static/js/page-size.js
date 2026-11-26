// Applies a new page size as soon as it is chosen. Without JavaScript the "Apply" button does the same.
(function () {
    var form = document.querySelector('form.page-size');
    if (!form) {
        return;
    }
    form.classList.add('auto-submit');
    form.querySelector('select').addEventListener('change', function () {
        form.submit();
    });
    // A page restored by the Back button must show the size it was rendered with.
    window.addEventListener('pageshow', function () {
        form.reset();
    });
})();
