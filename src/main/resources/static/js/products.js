// Progressive enhancement for the product list: submit the page-size form as soon as the
// selection changes. Without JavaScript the "Apply" button stays visible and does the same.
(function () {
    document.documentElement.classList.add('js');
    var select = document.getElementById('page-size');
    if (select && select.form) {
        select.addEventListener('change', function () {
            select.form.submit();
        });
    }
})();
