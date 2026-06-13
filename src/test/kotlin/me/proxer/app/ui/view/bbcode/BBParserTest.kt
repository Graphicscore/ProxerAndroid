package me.proxer.app.ui.view.bbcode

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import me.proxer.app.ui.view.bbcode.prototype.BoldPrototype
import me.proxer.app.ui.view.bbcode.prototype.ItalicPrototype
import me.proxer.app.ui.view.bbcode.prototype.StrikethroughPrototype
import me.proxer.app.ui.view.bbcode.prototype.TextPrototype
import me.proxer.app.ui.view.bbcode.prototype.UnderlinePrototype
import me.proxer.app.ui.view.bbcode.prototype.UrlPrototype
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BBParserTest {

    @Before fun mockTextPrototype() {
        mockkObject(TextPrototype)
        every { TextPrototype.construct(any<String>(), any<BBTree>()) } answers {
            BBTree(TextPrototype, secondArg(), args = BBArgs(text = firstArg<String>()))
        }
    }

    @After fun unmockTextPrototype() {
        unmockkObject(TextPrototype)
    }

    @Test fun `plain text becomes single TextPrototype child`() {
        val tree = BBParser.parse("Hello World")
        assertEquals(1, tree.children.size)
        assertEquals(TextPrototype, tree.children[0].prototype)
        assertEquals("Hello World", tree.children[0].args.text.toString())
    }

    @Test fun `empty string produces no children`() {
        val tree = BBParser.parse("")
        assertEquals(0, tree.children.size)
    }

    @Test fun `bold tag creates BoldPrototype with text child`() {
        val tree = BBParser.parse("[b]bold text[/b]")
        assertEquals(1, tree.children.size)
        assertEquals(BoldPrototype, tree.children[0].prototype)
        assertEquals(1, tree.children[0].children.size)
        assertEquals(TextPrototype, tree.children[0].children[0].prototype)
        assertEquals("bold text", tree.children[0].children[0].args.text.toString())
    }

    @Test fun `italic tag creates ItalicPrototype`() {
        val tree = BBParser.parse("[i]italic[/i]")
        assertEquals(ItalicPrototype, tree.children[0].prototype)
    }

    @Test fun `underline tag creates UnderlinePrototype`() {
        val tree = BBParser.parse("[u]underline[/u]")
        assertEquals(UnderlinePrototype, tree.children[0].prototype)
    }

    @Test fun `strikethrough tag creates StrikethroughPrototype`() {
        val tree = BBParser.parse("[s]strike[/s]")
        assertEquals(StrikethroughPrototype, tree.children[0].prototype)
    }

    @Test fun `url tag creates UrlPrototype`() {
        val tree = BBParser.parse("[url=https://proxer.me]Proxer[/url]")
        assertEquals(1, tree.children.size)
        assertEquals(UrlPrototype, tree.children[0].prototype)
    }

    @Test fun `nested bold inside italic`() {
        val tree = BBParser.parse("[i][b]bold italic[/b][/i]")
        assertEquals(ItalicPrototype, tree.children[0].prototype)
        val innerBold = tree.children[0].children[0]
        assertEquals(BoldPrototype, innerBold.prototype)
        assertEquals("bold italic", innerBold.children[0].args.text.toString())
    }

    @Test fun `text before and after tag`() {
        val tree = BBParser.parse("before [b]middle[/b] after")
        assertEquals(3, tree.children.size)
        assertEquals(TextPrototype, tree.children[0].prototype)
        assertEquals("before", tree.children[0].args.text.toString().trim())
        assertEquals(BoldPrototype, tree.children[1].prototype)
        assertEquals(TextPrototype, tree.children[2].prototype)
        assertEquals("after", tree.children[2].args.text.toString().trim())
    }

    @Test fun `unknown tag becomes TextPrototype fallback`() {
        val tree = BBParser.parse("[unknowntag]")
        assertEquals(1, tree.children.size)
        assertEquals(TextPrototype, tree.children[0].prototype)
        assertEquals("[unknowntag]", tree.children[0].args.text.toString())
    }

    @Test fun `unclosed bold tag has text in children`() {
        val tree = BBParser.parse("[b]no close tag")
        // Bold node is created but never closed; text is inside it
        assertEquals(1, tree.children.size)
        assertEquals(BoldPrototype, tree.children[0].prototype)
        assertEquals("no close tag", tree.children[0].children[0].args.text.toString())
    }

    @Test fun `multiple sibling tags`() {
        val tree = BBParser.parse("[b]one[/b][i]two[/i]")
        assertEquals(2, tree.children.size)
        assertEquals(BoldPrototype, tree.children[0].prototype)
        assertEquals(ItalicPrototype, tree.children[1].prototype)
    }
}
