-- Restore the original sample cover only when the book has no image reference.
-- Keep existing custom covers and invalidate stale administrator edits.
WITH restored AS (
    INSERT INTO product_images (product_id, image_order, image_url)
    SELECT p.id, 0, '/images/products/sci-tech-12.png'
    FROM products p
    WHERE p.id = 60 AND p.name = 'Algorithms to Live By'
      AND NOT EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = p.id)
    RETURNING product_id
)
UPDATE products SET version = version + 1
WHERE id IN (SELECT product_id FROM restored);
