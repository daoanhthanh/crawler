const table = $0;
const rows = table.querySelectorAll("tr");

const sessionValue = [...rows].map(
	(row) => [...row.querySelectorAll("td")][0].querySelector("a").pathname
);

// lấy các pathname dẫn đến api get-session và copy vào clipboard
copy(
	[...rows]
		.map(
			(row) =>
				"https://digdag-ee.pyxis-social.com/api" +
				[...row.querySelectorAll("td")][0].querySelector("a").pathname
		)
		.slice(0, 61)
);

// được kết quả như này
[
	"https://digdag-ee.pyxis-social.com/api/sessions/13941",
	"https://digdag-ee.pyxis-social.com/api/sessions/13929",
	"https://digdag-ee.pyxis-social.com/api/sessions/13917",
	"https://digdag-ee.pyxis-social.com/api/sessions/13905",
	"https://digdag-ee.pyxis-social.com/api/sessions/13893",
	"https://digdag-ee.pyxis-social.com/api/sessions/13881",
	// ...
];
