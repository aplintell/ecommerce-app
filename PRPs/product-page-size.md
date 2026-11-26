# PRP: Products per page control

**Target:** backend
**Status:** approved

## Goal
Let shoppers choose how many products the `/products` page shows. A "−  N per page  +" control at the top right of the
product list lowers or raises the page size, and the pagination ("Page X of Y") changes to match.

## User stories
- As a shopper, I want to show more products on one page, so that I can scan the catalog with fewer clicks.
- As a shopper, I want to show fewer products on one page, so that the page is shorter and quicker to read.

## Acceptance criteria
1. `/products` shows a page-size control at the top right of the list, next to the "Products" heading. It shows the
   current size ("12 per page" by default) with a "−" (fewer) and "+" (more) link.
2. Sizes go from 4 to 24 in steps of 4. The default stays 12. "−" is disabled at 4 and "+" is disabled at 24.
3. `GET /products?size=<n>` shows `n` products per page, and the page count follows (seed data, 19 active products:
   4 → 5 pages, 8 → 3, 12 → 2, 16 → 2, 20/24 → 1 page with no pagination).
4. Changing the size goes back to page 0 and keeps `q` and `category`.
5. Pagination links, "Back to first page", the search form and the category links keep the chosen `size`.
6. A missing, blank, non-numeric, out-of-range or off-step `size` (for example `abc`, `0`, `7`, `28`) falls back to 12
   and returns HTTP 200, never 400.
7. Existing catalog behavior and tests are unchanged.

## Implementation
- `CatalogService.parsePageSize` validates the `size` parameter; `listProducts` takes the page size.
- `ProductController` passes `pageSize`, `fewerPageSize` and `morePageSize` (null at the bounds) to the view.
- `products/list.html` renders the control and carries `size` in its links; `fragments/layout.html` keeps `size`
  in the search form and category nav when it is set.
- Plain links, no JavaScript, so it works the same as the existing server-rendered pagination.
